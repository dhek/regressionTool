package com.invixo.injection;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;

import org.apache.http.client.methods.HttpPost;

import com.invixo.common.GeneralException;
import com.invixo.common.IntegratedConfigurationMain;
import com.invixo.common.util.Logger;
import com.invixo.common.util.Util;
import com.invixo.consistency.FileStructure;
import com.invixo.injection.webServices.WebServiceHandler;
import com.invixo.main.GlobalParameters;


public class IntegratedConfiguration extends IntegratedConfigurationMain  {
	/*====================================================================================
	 *------------- Class variables
	 *====================================================================================*/
	private static Logger logger 			= Logger.getInstance();
	private static final String LOCATION 	= IntegratedConfiguration.class.getName();	
	private static final String MAP_FILE	= FileStructure.DIR_REGRESSION_OUTPUT_MAPPING + "Map_" + System.currentTimeMillis() + ".txt";
	public static BufferedWriter mapWriter	= null; 	// Writer for creating MAPPING file between original SAP message ID and new SAP message ID	
	
	
	/*====================================================================================
	 *------------- Instance variables
	 *====================================================================================*/
	private String payloadDirectory	= null;		// Directory containing payload files to be injected
	private ArrayList<InjectionRequest> injections 	= new ArrayList<InjectionRequest>();

	
	
	/*====================================================================================
	 *------------- Constructors
	 *====================================================================================*/
	public IntegratedConfiguration(String icoFileName) throws GeneralException {
		super(icoFileName);
		
		initialize();
	}
			
	
	
	/*====================================================================================
	 *------------- Getters and Setters
	 *====================================================================================*/
	public ArrayList<InjectionRequest> getInjections() {
		return this.injections;
	}	
	
	

	/*====================================================================================
	 *------------- Instance methods
	 *====================================================================================*/
	/**
	 * Inject FIRST payloads to SAP PO based on single ICO request file
	 */
	public void injectAllMessagesForSingleIco() throws InjectionException {
		final String SIGNATURE = "injectAllMessagesForSingleIco()";
		InjectionRequest ir = null;
		try {
			// Get list of all request/payload files related to ICO
			System.out.println(this.payloadDirectory);
			File[] files = Util.getListOfFilesInDirectory(this.payloadDirectory);
			logger.writeDebug(LOCATION, SIGNATURE, "Number of payload files to be processed: " + files.length);
			
			// Prepare
			if (files.length > 0) {
				// Only create mapping file if there are files to inject and if it is not created already
				initMappingTableWriter();
				
				// Extract common info from ICO
				RequestGeneratorUtil.extractInfoFromIcoRequest(this);
			}

			// Process each payload file
			for (File file : files) {
				ir = new InjectionRequest();
				injections.add(ir);
				injectMessage(file.getAbsolutePath(), ir);
			}
		} catch (InjectionPayloadException e) {
			// Add error to current payload injection
			if (ir != null ) {
				ir.setError(e);
			}
		} finally {
			try {
				// Logging
				String msg = "Number of processed payload files: " + this.injections.size();
				logger.writeDebug(LOCATION, SIGNATURE, msg);
				
				// Close resources
				if (mapWriter != null) {
					mapWriter.flush();
				}
			} catch (Exception e) {
				// Too bad...
			}
		}
	}


	/**
	 * Initialize writer enabling writing to mapping file for source/target SAP message IDs
	 * @throws InjectionPayloadException
	 */
	private static void initMappingTableWriter() throws InjectionPayloadException {
		final String SIGNATURE = "initMappingTableWriter()";
		try {
			if (mapWriter == null) {
				mapWriter = Files.newBufferedWriter(Paths.get(MAP_FILE), Charset.forName(GlobalParameters.ENCODING));
				logger.writeDebug(LOCATION, SIGNATURE, "SAP message Id mapping file initialized: " + MAP_FILE);
			}
		} catch (IOException e) {
			String msg = "Error initializing SAP MessageId mapping file "+ MAP_FILE + ".\n" + e.getMessage();
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new InjectionPayloadException(msg);
		}
	}
	
	
	/**
	 * Main entry point for processing/injecting a single payload file to SAP PO.
	 * Create HTTP request message to be sent to SAP PO and inject it to the system.
	 * Routing info is extracted from the ICO Request file.
	 * Payload is taken from the referenced payload file.
	 * @param payloadFile
	 * @param ir
	 * @throws InjectionPayloadException
	 */
	private void injectMessage(String payloadFile, InjectionRequest ir) throws InjectionPayloadException {
		final String SIGNATURE = "injectMessage(String)";
		try {
			logger.writeDebug(LOCATION, SIGNATURE, "---- Payload processing BEGIN: " + payloadFile);
			ir.setSourcePayloadFile(payloadFile);

			// Add payload to injection request. Payload is taken from an "instance" payload file (a file extracted previously)
			byte[] payload = Util.readFile(payloadFile);

			// Generate SOAP XI Header
			String soapXiHeader = RequestGeneratorUtil.generateSoapXiHeaderPart(this, ir);
			
			// Build Request to be sent via Web Service call
			HttpPost webServiceRequest = WebServiceHandler.buildHttpPostRequest(soapXiHeader.getBytes(GlobalParameters.ENCODING), payload); 
			
			// Store request on file system (just for pleasant reference)
			ir.setInjectionRequestFile(getTargetFileName(this.fileName, ir.getMessageId()));
			webServiceRequest.getEntity().writeTo(new FileOutputStream(new File(ir.getInjectionRequestFile())));
			logger.writeDebug(LOCATION, SIGNATURE, "Request message to be sent to SAP PO is stored here: " + ir.getInjectionRequestFile());
	        
			// Call SAP PO Web Service (using XI protocol)
			WebServiceHandler.callWebService(webServiceRequest);
			
			// Write entry to mapping file
			addMappingEntryToFile(Util.getFileName(payloadFile, false), ir.getMessageId());
		} catch (IOException e) {
			String msg = "Error injecting new request to SAP PO for ICO file " + super.fileName + " and payload file " + payloadFile + ".\n" + e.getMessage();
			logger.writeError(LOCATION, SIGNATURE, msg);
			throw new InjectionPayloadException(msg);
		} finally {
			logger.writeDebug(LOCATION, SIGNATURE, "---- Payload processing END");
		}
	}
	
	
	/**
	 * Write new SAP Message ID (source --> target) mapping to mapping file 
	 * @param sourceMsgId
	 * @param targetMsgId
	 * @throws IOException
	 */
	private void addMappingEntryToFile(String sourceMsgId, String targetMsgId) throws IOException {
		final String SIGNATURE = "addMappingEntryToFile(String, String)";
		final String separator = "|";
		
		// Create mapping line
		String mapEntry = sourceMsgId + separator + targetMsgId + "\n";
		
		// Write line to map
		mapWriter.write(mapEntry);
		
		logger.writeDebug(LOCATION, SIGNATURE, "Map file update with new entry: " + mapEntry);
	}
	
	
	private static String getTargetFileName(String icoRequestfile, String messageId) {
		String scenarioName = Util.getFileName(icoRequestfile, false);
		String targetFile = FileStructure.DIR_REGRESSION_INPUT_INJECTION + scenarioName + " -- " +  messageId + ".payload";
		return targetFile;
	}
	
	
	/**
	 * Implementation specific object initialization
	 */
	protected void initialize() throws GeneralException {
		final String SIGNATURE = "initialize()";

		// Set directory for Payloads (FIRST)
		this.payloadDirectory = FileStructure.DIR_REGRESSION_OUTPUT_PAYLOADS_FIRST_MSG_VERSION + super.name;
		logger.writeDebug(LOCATION, SIGNATURE, "Source directory containing FIRST messages: " + this.payloadDirectory);
	}
	
}