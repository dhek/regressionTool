package com.invixo.compare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.events.XMLEvent;

import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.Difference;

import com.invixo.common.util.Logger;
import com.invixo.common.util.Util;
import com.invixo.consistency.FileStructure;
import com.invixo.main.GlobalParameters;
import com.invixo.main.Main;


public class IntegratedConfiguration {
	private static Logger logger = Logger.getInstance();
	private static final String LOCATION = IntegratedConfiguration.class.getName();
	private List<Path> sourceFiles;
	private List<Path> compareFiles;
	private static Map<String, String> messageIdMap;
	private	List<String> compareExceptions;
	private String sourceIcoName;
	private int compareCount = 0;
	private int compareExceptionsUsed = 0;
	private int compareErrorsFound = 0;
	private List<UUID> sourceMsgIdsProcessed = new ArrayList<UUID>();
	private List<UUID> compareMsgIdsProcessed = new ArrayList<UUID>();

	
	/**
	 * Class constructor
	 * @param sourceIcoPath
	 * @param compareIcoPath
	 * @param icoName
	 * @throws CompareException 
	 */
	public IntegratedConfiguration(String sourceIcoPath, String compareIcoPath, String icoName) throws CompareException {
		String SIGNATURE = "IntegratedConfiguration(String sourceIcoPath, String copmareIcoPath, String icoName";
		logger.writeDebug(LOCATION, SIGNATURE, "Initialize compare data of ICO compare");
		
		// Set current ICO
		this.sourceIcoName = icoName;
		
		// Get files from source and compare directories
		sourceFiles = Util.generateListOfPaths(sourceIcoPath.toString(), "FILE");
		compareFiles = Util.generateListOfPaths(compareIcoPath.toString(), "FILE");
		
		// Build message id map to match "Prod"(source) and "Test"(compare) messages
		messageIdMap = buildMessageIdMap(FileStructure.DIR_INJECT);
		
		// Build exception map to be used to exclude data elements in later compare
		compareExceptions = buildCompareExceptionMap(FileStructure.DIR_CONFIG + "\\compareExceptions.xml");
		
	}

	
	private List<String> buildCompareExceptionMap(String icoExceptionFilePath) throws CompareException {
		String SIGNATURE = "buildCompareExceptionMap(String)";
		logger.writeDebug(LOCATION, SIGNATURE, "Building MAP of exceptions using data from: " + icoExceptionFilePath);
		
		// Get all exceptions listed in files found 
		List<String> compareExceptions = extractIcoCompareExceptionsFromFile(icoExceptionFilePath);
		
		
		// Return exception map
		return compareExceptions;
	}

	
	private List<String> extractIcoCompareExceptionsFromFile(String icoExceptionFilePath) throws CompareException {
		final String SIGNATURE = "extractIcoCompareExceptionsFromFile(String)";
		List<String> icoExceptions = new ArrayList<String>();
		try {
			InputStream fileStream = new FileInputStream(icoExceptionFilePath);		
			XMLInputFactory factory = XMLInputFactory.newInstance();
			XMLEventReader eventReader = factory.createXMLEventReader(fileStream);
			boolean correctIcoFound = false;
			while (eventReader.hasNext()) {
			    XMLEvent event = eventReader.nextEvent();
			    
			    switch(event.getEventType()) {
			    case XMLStreamConstants.START_ELEMENT:
			    	String currentStartElementName = event.asStartElement().getName().getLocalPart();
			    	if ("name".equals(currentStartElementName)) {
						if (this.sourceIcoName.equals(eventReader.peek().asCharacters().getData())) {
							
							// We are at the correct ICO element
							correctIcoFound = true;
						}
					}
			    	if ("xpath".equals(currentStartElementName) && correctIcoFound) {
			    		// Add exeption data if we are at the right ICO and correct element
			    		icoExceptions.add(eventReader.peek().asCharacters().getData());
			    	}
			    	break;
			    case XMLStreamConstants.END_ELEMENT:
			    	String currentEndElementName = event.asEndElement().getName().getLocalPart();
			    	if ("integratedConfiguration".equals(currentEndElementName)) {
			    		// We don't want to read any more ICO data
			    		correctIcoFound = false;
					}
			    	break;
			    }
			}
		} catch (Exception e) {
			String msg = "Error extracting exception.\n" + e.getMessage();
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new CompareException(msg);
		}
		
		// Return exceptions found
		return icoExceptions; 
	}


	private static Map<String, String> buildMessageIdMap(String mappingDir) throws CompareException {
		String SIGNATURE = "buildMessageIdMap(String)";
		try {
			logger.writeDebug(LOCATION, SIGNATURE, "Building MAP of message ID's for source and compare files from: " + mappingDir);
			
			// Build path to mapping file generated during inject
			String mappingFilePath = mappingDir + Main.PARAM_VAL_SOURCE_ENV + "_to_" + Main.PARAM_VAL_TARGET_ENV + "_msgId_map.txt";
			
			// Read file to Path
			Path mapFilePath = new File(mappingFilePath).toPath();	
			
			// Create map splitting on delimiter | from map file
	        Map<String, String> mapFromFile = Util.createMapFromPath(mapFilePath, "\\|", 0, 1);
			
	        // Return map
	        return mapFromFile;
	        
		} catch (IOException e) {
			String msg = "ERROR | Can't read msgId map from: " + mappingDir + "\n" + e.getMessage();
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new CompareException(msg);
		}
	}

	
	public void start() throws CompareException {
		String SIGNATURE = "start()";
		logger.writeDebug(LOCATION, SIGNATURE, "Processing ICO data of: \"" + this.sourceIcoName + "\" start");

		if(this.sourceFiles.size() == this.compareFiles.size()) {
			// Start looping over source files
			Path currentSourcePath;
			for (int i = 0; i < sourceFiles.size(); i++) {

				// Get matching compare file using message id map
				currentSourcePath = sourceFiles.get(i); 

				// Prepare: Locate matching compare file based on source msgId
				Path comparePathMatch = getMatchingCompareFile(currentSourcePath, compareFiles, IntegratedConfiguration.messageIdMap);

				// Compare
				compareFiles(currentSourcePath, comparePathMatch);

				// Increment compare count
				this.compareCount++;
			}
			
		} else {
			String msg = "Compare error, source and compare files mismatch sources: " + this.sourceFiles.size() + " targets: " + this.compareFiles.size();
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new CompareException(msg);
		}
		
		logger.writeDebug(LOCATION, SIGNATURE, "Processing ICO data of: \"" + this.sourceIcoName + "\" completed!");
	}
	
	
	private static Path getMatchingCompareFile(Path sourceFilePath, List<Path> compareFiles, Map<String, String> map) throws CompareException {
		String SIGNATURE = "getMatchingCompareFile(Path, List<Path>, Map<String, String>)";
		
		// Extract message id from filename 
		String sourceMsgId = Util.getFileName(sourceFilePath.getFileName().toString(), false);
		
		logger.writeDebug(LOCATION, SIGNATURE, "Prepare: Getting matching compare file for sourceId: " + sourceMsgId);

		// Get compare message id from map using source id
		String compareMsgId = map.get(sourceMsgId);
		
		logger.writeDebug(LOCATION, SIGNATURE, "Match found, compare file msgId: " + compareMsgId);
		
		try {
			// Search for compare id in compare file list
			Path compareFileFound = sourceFilePath;
			for (int i = 0; i < compareFiles.size(); i++) {
				String currentFile = compareFiles.get(i).getFileName().toString();

				if (currentFile.toString().contains(compareMsgId)) {
					// Get current file if we have a match
					compareFileFound = compareFiles.get(i);

					// Stop searching
					break;
				}

			}		
			// return compare file found
			return compareFileFound;
			
		} catch (NullPointerException e) {
			String msg = "No matching message id found for: " + sourceMsgId + " " + e.getMessage();
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new CompareException(msg);
		}
	}
	
	private void compareFiles(Path sourcePath, Path comparePath) throws CompareException {
		String SIGNATURE = "compareFiles(Path, Path)";
		logger.writeDebug(LOCATION, SIGNATURE, "Start comparring: " + sourcePath + " and " + comparePath);
		
		// TODO: how do we check the mime-type of the message, xml, text, etc - for now we assume payloads are always xml.
		this.doXmlCompare(sourcePath, comparePath);
		
		logger.writeDebug(LOCATION, SIGNATURE, "Compare done!");
	}
	
	
	private void doXmlCompare(Path sourcePath, Path comparePath) throws CompareException {
		String SIGNATURE = "doXmlCompare(Path, Path)";
		String sourceFileString = null;
		String compareFileString = null;
		try {
			// Prepare files for compare
			sourceFileString = Util.inputstreamToString(new FileInputStream(sourcePath.toFile()), GlobalParameters.ENCODING);
			compareFileString = Util.inputstreamToString(new FileInputStream(comparePath.toFile()), GlobalParameters.ENCODING);

			// Compare string representations of source and compare payloads
			Diff xmlDiff = DiffBuilder
					.compare(sourceFileString)
					.withTest(compareFileString)
					.withDifferenceEvaluator(new CustomDifferenceEvaluator(this.compareExceptions))
					.ignoreWhitespace()
					.normalizeWhitespace()
					.build();
			
			// Handle compare result
			handleCompareResult(xmlDiff, sourcePath.getFileName().toString(), comparePath.getFileName().toString());

		} catch (FileNotFoundException e) {
			String msg = "Problem converting source and/or compare payloads to string\n" + e.getMessage();
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new CompareException(msg);
		}
	}
	
	private void handleCompareResult(Diff xmlDiff, String sourceFileName, String compareFileName) {
		String SIGNATURE = "handleCompareResult(Diff, String, String)";
		String result =	"Result:\n--------------------------------------------------\n";
		
		Iterable<Difference> diffs = xmlDiff.getDifferences();
		int diffErrors = 0;
		for (Difference d : diffs) {
			result += d.getComparison() + "\n";
			diffErrors++;
		}		
		
		logger.writeDebug(LOCATION, SIGNATURE, "Differences found during compare: " + diffErrors);

		// Write result to file system
		writeCompareResultToFile(sourceFileName, compareFileName, result, diffErrors);
	}


	private void writeCompareResultToFile(String sourceFileName, String compareFileName, String result, int diffErrors) {
		// Make sure we have a results+ICO directory to write results
		String targetResultDir = FileStructure.DIR_REPORTS + this.sourceIcoName;
		FileStructure.createDirIfNotExists(targetResultDir);

		// Build final result path
		String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(new Date());
		String resultFilePath = targetResultDir + "\\" + timeStamp + "_Errors_" + diffErrors + "_" + sourceFileName + " vs. " + compareFileName + ".txt";

		// Write to file system
		Util.writeFileToFileSystem(resultFilePath , result.getBytes());
	}

}