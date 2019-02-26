package com.invixo.common;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.invixo.common.util.Logger;
import com.invixo.consistency.FileStructure;
import com.invixo.main.GlobalParameters;

public class StateHandler {
	private static Logger logger = Logger.getInstance();
	private static final String LOCATION = StateHandler.class.getName();
	
	private static final String INJECT_FIRST_MSG_ID_TEMPLATE = "<TEMPLATE_INJECT_FIRST_MSG_ID>";
	private static final String NON_INIT_LAST_MSG_ID_TEMPLATE = "<TEMPLATE_NON_INIT_LAST_MSG_ID>";
	private static final String NON_INIT_LAST_MSG_KEY_TEMPLATE = "<TEMPLATE_NON_INIT_LAST_MSG_KEY>";
	private static final String NON_INIT_LAST_FILE_NAME_TEMPLATE = "<TEMPLATE_NON_INIT_LAST_FILE_NAME>";
	private static final String SEPARATOR = GlobalParameters.FILE_DELIMITER;
	
	private static HashMap<String, String> tempMsgLink = new HashMap<String, String>();	// Map of <FIRST msg Id, Inject Id> created during inject.
	private static List<String> icoLines = null;		// All lines of an ICO state file
	private static Path icoStatePath =  null;			// Path to an ICO state file
	

	public static void setIcoPath(String icoName) {
		icoStatePath = Paths.get(FileStructure.FILE_STATE_PATH + icoName + ".txt");
	}
	

	public static void storeIcoState() throws StateException {
		final String SIGNATURE = "storeIcoState()";
		try {
			// Delete existing state file
			reset();
			
			// Create file writer
			BufferedWriter bw = Files.newBufferedWriter(icoStatePath);

			// Write header line to file
			final String headerLine	= "TimeInMillis"
									+ SEPARATOR
									+ "InitExtractFirst_MsgKey"
									+ SEPARATOR
									+ "InitExtractFirst_MsgId"
									+ SEPARATOR
									+ "InitExtractFirst_FileName"
									+ SEPARATOR
									+ "InitExtractLast_MsgKey"
									+ SEPARATOR
									+ "InitExtractLast_MsgId"
									+ SEPARATOR
									+ "InitExtractLast_FileName"
									+ SEPARATOR
									+ "inject_MsgId"
									+ SEPARATOR
									+ "NonInitExtractLast_MsgKey"
									+ SEPARATOR
									+ "NonInitExtractLast_MsgId"
									+ SEPARATOR
									+ "NonInitExtractLast_FileName"
									+ SEPARATOR
									+ "IcoName";
			bw.write(headerLine);
			bw.newLine();
							
			// Write lines to file
			for (String line : icoLines) {
				bw.write(line);
				bw.newLine();
			}

			// Cleanup
			bw.flush();
			bw.close();	
			
			logger.writeInfo(LOCATION, SIGNATURE, "ICO State persisted to file: " + icoStatePath);
		} catch (IOException e) {
			String msg = "Error updating state file: " + icoStatePath + ".\n" + e;
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new StateException(msg);
		}
	}
	
	
	/**
	 * Read all ICO State lines from File.
	 * @return
	 * @throws StateException
	 */
	public static List<String> readIcoStateLinesFromFile() throws StateException {
		final String SIGNATURE = "readIcoStateLinesFromFile()";
		try {
			if (icoLines == null) {
				icoLines = Files.readAllLines(icoStatePath);
				icoLines.remove(0); // remove header line
			}
			
			return icoLines;
		} catch (IOException e) {
			String msg = "Error reading ICO lines from state file: " + icoStatePath.toString() + "\n" + e;
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new StateException(msg);
		}
	}
	
	
	/**
	 * Scenario: Extract Init
	 * Create an entry.
	 * @param icoName
	 * @param first
	 * @param last
	 * @return
	 */
	public static String createExtractEntry(String icoName, Payload first, Payload last) {
		return createEntry(	icoName, 
							first, 
							last, 
							INJECT_FIRST_MSG_ID_TEMPLATE, 
							NON_INIT_LAST_MSG_KEY_TEMPLATE,
							NON_INIT_LAST_MSG_ID_TEMPLATE,
							NON_INIT_LAST_FILE_NAME_TEMPLATE
							);
	}


	private static String createEntry(	String icoName, 
										Payload first, 
										Payload last, 
										String injectFirstMsgId, 
										String nonInitLastMsgKey, 
										String nonInitLastMsgId, 
										String nonInitLastFileName) {
		String line	= System.currentTimeMillis() 
					+ SEPARATOR 
					
					// INIT FIRST payload
					+ first.getSapMessageKey()
					+ SEPARATOR 
					+ first.getSapMessageId()
					+ SEPARATOR 
					+ first.getFileName()
					+ SEPARATOR
					
					// INIT LAST payload
					+ last.getSapMessageKey()
					+ SEPARATOR 
					+ last.getSapMessageId()
					+ SEPARATOR
					+ last.getFileName()
					+ SEPARATOR
					
					// INJECT Message Id
					+ injectFirstMsgId
					+ SEPARATOR

					// NON-INIT LAST payload
					+ nonInitLastMsgKey
					+ SEPARATOR 
					+ nonInitLastMsgId
					+ SEPARATOR
					+ nonInitLastFileName
					+ SEPARATOR
					
					// ICO identifier
					+ icoName;
		
		return line;
	}

	
	/**
	 * Scenario: Extract
	 * @param stateEntry
	 */
	public static void addEntryToInternalList(String stateEntry) {
		if (icoLines == null) {
			icoLines = new ArrayList<String>();
		}
		icoLines.add(stateEntry);
	}
		
	
	public static void reset() throws StateException {
		final String SIGNATURE = "reset()";
		try {
			Files.deleteIfExists(icoStatePath);	
		} catch (IOException e) {
			String msg = "Error deleting state file: " + icoStatePath.toString() + "\n" + e;
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new StateException(msg);
		}	
	}
	
	
	/**
	 * Get list of unique FIRST file names from a list of State lines.
	 * @param lines				Lines to extract unique FIRST IDs from
	 * @return
	 */
	public static HashSet<String> getUniqueFirstFileNames(List<String> lines) {
		HashSet<String> uniqueFirstIds = new HashSet<String>();
		
		for (String line : lines) {
			String messageId = line.split(SEPARATOR)[3];		// File name for a Source/original FIRST message Id
			uniqueFirstIds.add(messageId);
		}
		
		return uniqueFirstIds;
	}
	
	
	/**
	 * Scenario: Inject
	 * Replace INJECT_TEMPLATE with inject Message Id, for all lines containing the referenced 'initFirstMsgId' in internal
	 * map of <initFirstMsgId, injectId>.
	 * Replacement does not store data, it merely updates the internal reference to the State Lines in memory. 
	 */
	public static void replaceInjectTemplateWithId() {
		// Modify internal list of ICO lines
		for (int i = 0; i < icoLines.size(); i++) {
			String line = icoLines.get(i);
			// Split
			String[] lineParts = line.split(SEPARATOR);
			
			// Get FIRST message id
			String currentFirstMsgId = lineParts[2];
			
			// Determine if message id of current line needs to be updated
			boolean isMatchFound = tempMsgLink.containsKey(currentFirstMsgId);
			
			// Replace inject template text with inject id, if the 2 FIRST message ids are the same
			if (isMatchFound) {
				String injectId = tempMsgLink.get(currentFirstMsgId);
				line = line.replace(INJECT_FIRST_MSG_ID_TEMPLATE, injectId);
				icoLines.set(i, line);
			}
		}
	}
	
	
	/**
	 * Scenario: Extract NonInit, multimapping
	 * @param injectMessageId
	 * @param initlastMessageKey
	 * @param nonInitLastMessageKey
	 * @param nonInitLastMessageId
	 */
	public static void replaceMessageInfoTemplateWithMessageInfo(String injectMessageId, String initlastMessageKey, String nonInitLastMessageKey, String nonInitLastMessageId) {
		// Get sequence id from Message Key
		String nonInitLastMessageKeySequenceId = getSequenceIdFromMessageKey(nonInitLastMessageKey);
		
		for (int i = 0; i < icoLines.size(); i++) {
			String line = icoLines.get(i);
			
			// Get parts from current line
			String[] lineParts = line.split(SEPARATOR);
			String currentLastMessageKey = lineParts[4];
			String currentLastKeySequenceId = getSequenceIdFromMessageKey(currentLastMessageKey); 
			String currentInjectMessageId = lineParts[7];

			// Replace templates
			if (nonInitLastMessageKeySequenceId.equals(currentLastKeySequenceId) && injectMessageId.equals(currentInjectMessageId)) {
				line = line.replace(NON_INIT_LAST_MSG_KEY_TEMPLATE, nonInitLastMessageKey);
				line = line.replace(NON_INIT_LAST_MSG_ID_TEMPLATE, nonInitLastMessageId);
				icoLines.set(i, line);		
			}
		}
	}
	
	
	/**
	 * Scenario: Inject
	 * @param firstMsgId
	 * @param injectMsgId
	 */
	public static void addInjectEntry(String firstMsgId, String injectMsgId) {
		tempMsgLink.put(firstMsgId, injectMsgId);
	}
	
	
	/**
	 * Scenario: Extract NonInit
	 * Create map from ICO State Lines.
	 * @return					Map<key, value>
	 * 								KEY: Source message id (original extracted message id (INIT extract))
	 * 								VAL: Target message id (inject message id)
	 * @throws StateException
	 */
	public static Map<String, String> getMessageIdsFromFile() throws StateException {
		Map<String, String> map = convertLineInfoToMap(2, 7);
		return map;
	}
	
	
	/**
	 * Scenario: Compare
	 * Create map from ICO State Lines.
	 * @return					Map<key, value>
	 * 								KEY: Source message id (init LAST message id)
	 * 								VAL: Target message id (non-init LAST mesage id)
	 * @throws StateException
	 */
	public static Map<String, String> getCompareMessageIdsFromIcoLines() throws StateException {
		Map<String, String> map = convertLineInfoToMap(5, 9);
		return map;
	}
	
	
	private static Map<String, String> convertLineInfoToMap(int keyIndex, int valueIndex) throws StateException {
		// Read lines from file (sets internal property)
		readIcoStateLinesFromFile();
		
		// Create map
		Map<String, String> map = new HashMap<String, String>();
		for (String line : icoLines) {
			String key 		= line.split(SEPARATOR)[keyIndex];
			String value 	= line.split(SEPARATOR)[valueIndex];
			map.put(key, value);
		}
		
		// Return map
		return map;
	}
	

	private static String getSequenceIdFromMessageKey(String messageKey) {
		String sequenceId = messageKey.substring(messageKey.indexOf("EOIO"), messageKey.length());
		return sequenceId;
	}
	
	
	public static String getIcoPath() {
		return icoStatePath.toString();
	}


	/**
	 * Scenario: Extract
	 * @param sapMessageId
	 * @param fileName
	 */
	public static void replaceLastFileNameTemplateWithFileName(String sapMessageId, String fileName) {
		for (int i = 0; i < icoLines.size(); i++) {
			String line = icoLines.get(i);
			String currentNonInitLastMessageId = line.split(SEPARATOR)[9];
			
			if (sapMessageId.equals(currentNonInitLastMessageId)) {
				line = line.replace(NON_INIT_LAST_FILE_NAME_TEMPLATE, fileName);
				icoLines.set(i, line);
			}
		}
	}
	
}
