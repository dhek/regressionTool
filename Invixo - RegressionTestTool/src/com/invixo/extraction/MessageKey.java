package com.invixo.extraction;

import java.io.IOException;
import java.util.ArrayList;

import com.invixo.common.util.Logger;
import com.invixo.common.util.Util;
import com.invixo.common.Payload;
import com.invixo.common.PayloadException;
import com.invixo.common.StateHandler;
import com.invixo.common.util.HttpException;
import com.invixo.main.GlobalParameters;

public class MessageKey {
	private static Logger logger = Logger.getInstance();
	private static final String LOCATION = MessageKey.class.getName();	

	private String sapMessageKey = null;			// SAP Message Key from Web Service response of GetMessageList
	private String sapMessageId = null;				// SAP Message Id 
	private IntegratedConfiguration ico	= null;		// Integrated Configuration
	private Payload payloadFirst = new Payload(); 	// FIRST payload
	private Payload payloadLast = new Payload();	// LAST payload
	private ArrayList<String> multiMapMessageKeys;	// List of Parent Message Keys in the case of Multimapping scenario
	private Exception ex = null;					// Error details
	private static int sequenceCounter = 0;
	
	
	
	MessageKey(IntegratedConfiguration ico, String messageKey) {
		this.ico = ico;
		this.setSapMessageKey(messageKey);
		this.setSapMessageId(messageKey);
		
		if (this.ico.isUsingMultiMapping()) {
			this.multiMapMessageKeys = new ArrayList<String>();
		}
	}

	
	public String getSapMessageKey() {
		return sapMessageKey;
	}
	
	
	public void setSapMessageKey(String sapMessageKey) {
		this.sapMessageKey = sapMessageKey;
	}
	
	
	public String getSapMessageId() {
		return sapMessageId;
	}
	
	
	public void setSapMessageId(String sapMessageKey) {
		this.sapMessageId = Util.extractMessageIdFromKey(sapMessageKey);
	}

	
	public Exception getEx() {
		return ex;
	}
	
	
	public void setEx(Exception e) {
		this.ex = e;
	}
	
	
	public ArrayList<String> getMultiMapMessageKeys() {
		return multiMapMessageKeys;
	}
	
	
	public Payload getPayloadFirst() {
		return payloadFirst;
	}

	
	public Payload getPayloadLast() {
		return payloadLast;
	}

	
	/**
	 * Main entry point
	 * Extract FIRST and/or LAST payload.
	 * @param messageKey
	 * @throws ExtractorException
	 */
	void extractAllPayloads(String messageKey) throws ExtractorException {
		// Extract FIRST payload
		if (Boolean.parseBoolean(GlobalParameters.PARAM_VAL_EXTRACT_MODE_INIT)) {
			this.payloadFirst = this.getBasicFirstInfo(messageKey);
		}
			
		// Extract LAST payload
		this.payloadLast = this.extractLastPayload(messageKey);
	}
	
	
//	void storeState(String injectMessageId, Payload first, Payload last) throws ExtractorException {
//		final String SIGNATURE = "storeState(String, Payload, Payload)";
//		try {
//			boolean isInitMode = Boolean.parseBoolean(GlobalParameters.PARAM_VAL_EXTRACT_MODE_INIT);
//			
//			if (isInitMode) {
//				// Persist message: FIRST
//				first.persistMessage(this.ico.getFilePathFirstPayloads());
//			}
//			
//			// Persist message: LAST
//			last.persistMessage(this.ico.getFilePathLastPayloads());
//			
//			// Build and add new State entry line
//			if (isInitMode) {
//				// Extract: Init
//				String newEntry = StateHandler.createExtractEntry(this.ico.getName(), first, last, ++sequenceCounter);
//				StateHandler.addEntryToInternalList(newEntry);
//			} else {
//				// Extract: Non-init
//				StateHandler.addNonInitMessageInfoToInternalList(
//									injectMessageId, 
//									last.getSapMessageKey(), 
//									last.getSapMessageId(), 
//									last.getFileName(),
//									++sequenceCounter);
//			}
//		} catch (PayloadException e) {
//			String msg = "Error persisting payload for MessageKey!\n" + e;
//			logger.writeError(LOCATION, SIGNATURE, msg);
//			ExtractorException ex = new ExtractorException(msg);
//			this.ex = ex;
//			throw ex;
//		} 
//	}
	
	
	/**
	 * Extract FIRST or LAST payload for ICOs with a mapping multiplicity of 1:1.
	 * Call Web Service for fetching SAP PO message data (SOAP envelope). 
	 * A normal web service response will contain an XML payload containing base64 encoded SAP XI multipart message.
	 * This method is responsible for creating a Payload object.
	 * @param messageKey
	 * @param isFirst
	 * @return
	 * @throws ExtractorException			Other errors during extraction
	 * @throws HttpException				Web Service call failed
	 * @throws PayloadException				Error setting state on Payload
	 */
	private Payload extractPayload(String messageKey, boolean isFirst) throws ExtractorException, HttpException, PayloadException {
		final String SIGNATURE = "extractPayload(String, boolean)";
		try {
			logger.writeDebug(LOCATION, SIGNATURE, "MessageKey [" + (isFirst?"FIRST":"LAST") + "] processing started...");
			
			// Lookup SAP XI Message
			String base64EncodedMessage = WebServiceUtil.lookupSapXiMessage(messageKey, isFirst);

			// Create Payload object
			Payload payload = new Payload();
			payload.setSapMessageKey(messageKey);
			
			// Check if payload was found
			if ("".equals(base64EncodedMessage)) {
				logger.writeDebug(LOCATION, SIGNATURE, "Web Service response contains no XI message.");
				payload.setPayloadFoundStatus(Payload.STATUS.NOT_FOUND);
			} else {
				logger.writeDebug(LOCATION, SIGNATURE, "Web Service response contains XI message.");
				payload.setPayloadFoundStatus(Payload.STATUS.FOUND);
				payload.setMultipartBase64Bytes(base64EncodedMessage);
			}
						
			return payload;
		} catch (IOException e) {
			String msg = "Error reading all bytes from generated web service request\n" + e;
			logger.writeError(LOCATION, SIGNATURE, msg);
			ExtractorException ex = new ExtractorException(msg);
			this.ex = ex;
			throw ex;
		} finally {
			logger.writeDebug(LOCATION, SIGNATURE, "MessageKey [" + (isFirst?"FIRST":"LAST") + "] processing finished...");
		}
	}

	
	/**
	 * Extract original FIRST message from PO of a MultiMapping interface (1:n multiplicity).
	 * NB:	for a multimapping scenario GetMessageList always returns LAST message keys. This is why these 
	 * 		require translation into a FIRST message key.
	 * @param messageId
	 * @return
	 * @throws ExtractorException
	 * @throws PayloadException
	 */
	Payload processMessageKeyMultiMapping(String messageId) throws ExtractorException, PayloadException {
		final String SIGNATURE = "processMessageKeyMultiMapping(String)";
		try {
			logger.writeDebug(LOCATION, SIGNATURE, "MessageKey [FIRST] MultiMapping processing start");
			
			String parentId = messageId;
			if (Boolean.parseBoolean(GlobalParameters.PARAM_VAL_EXTRACT_MODE_INIT)) {
				// Lookup parent (FIRST) Message Id
				parentId = WebServiceUtil.lookupPredecessorMessageId(messageId, this.ico.getName());
			}

			// Lookup parent (FIRST) Message Key
			String messageKey = WebServiceUtil.lookupMessageKey(parentId, this.ico.getName());
			
			// Many of the Message IDs returned by GetMessageList response may have the same parent.
			// We are only interested in extracting data for the parent once.
			Payload payload = null;
			if (ico.getMultiMapFirstMsgKeys().contains(messageKey)) {
				// MessageKey already processed and FIRST message details already found.
				// Do nothing
				logger.writeDebug(LOCATION, SIGNATURE, "Skip looking up FIRST msg, since previously found for current message key: " + messageKey);
			} else {
				// Fetch FIRST payload using the original FIRST messageKey
				payload = new Payload();
				payload.setSapMessageKey(messageKey);

				// Add current, processed MessageKey to complete list of unique, previously found, FIRST payloads
				ico.getMultiMapFirstMsgKeys().add(messageKey);
			}
			
			// Return;
			return payload;
		} catch (HttpException e) {
			String msg = "Error during web service call\n" + e;
			logger.writeError(LOCATION, SIGNATURE, msg);
			ExtractorException ex = new ExtractorException(msg);
			this.ex = ex;
			throw ex;
		} finally {		
			logger.writeDebug(LOCATION, SIGNATURE, "MessageKey [FIRST] MultiMapping processing finished...");
		}
	}


	/**
	 * Get basic FIRST message info (Message Key and Message Id)
	 * @param key
	 * @return
	 * @throws PayloadException
	 * @throws ExtractorException 
	 */
	Payload getBasicFirstInfo(String key) throws ExtractorException {
		final String SIGNATURE = "getBasicFirstInfo(String)";
		try {
			Payload payload = null;
			// Process according to multiplicity
			if (this.ico.isUsingMultiMapping()) {
				// Fetch payload: FIRST for multimapping interface (1:n multiplicity)
				payload = this.processMessageKeyMultiMapping(Util.extractMessageIdFromKey(key));
			} else {
				// Fetch payload: FIRST for non-multimapping interface (1:1 multiplicity)	
				payload = new Payload();
				payload.setSapMessageKey(key);
			}
			
			return payload;
		} catch (PayloadException e) {
			this.setEx(e);
			String msg = "Error finding basic FIRST info for key: " + key + "\n" + e;
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new ExtractorException(msg);
		}
	}

	
	/**
	 * Extract LAST payload.
	 * @param key
	 * @returns
	 * @throws ExtractorException 
	 */
	private Payload extractLastPayload(String key) throws ExtractorException {
		final String SIGNATURE = "extractLastPayload(String)";
		try {
			// Fetch payload: LAST
			Payload payload = extractPayload(key, false);
			return payload;
		} catch (PayloadException|ExtractorException|HttpException e) {
			this.setEx(e);
			String msg = "Error processing LAST key: " + key + "\n" + e;
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new ExtractorException(msg);
		}
	}

}