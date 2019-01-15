package com.invixo.messageExtractor.httpHandlers;

import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

import com.invixo.common.util.Logger;
import com.invixo.common.util.PropertyAccessor;
import com.invixo.common.util.Util;

public abstract class WebServiceHandler {
	private static Logger logger = Logger.getInstance();
	private static final String LOCATION = WebServiceHandler.class.getName();
	private static final boolean ENABLE_WS_LOGGING = Boolean.valueOf(PropertyAccessor.getProperty("ENABLE_WS_LOGGING"));	
	private static final String ENCODING = PropertyAccessor.getProperty("ENCODING");
	private static final String WEB_SERVICE_USER = PropertyAccessor.getProperty("USER");
	private static final String WEB_SERVICE_PASS = PropertyAccessor.getProperty("PASSWORD");
	private static final String SERVICE_HOST_PORT = PropertyAccessor.getProperty("SERVICE_HOST_PORT");
	private static final String SERVICE_PATH_EXTRACT = PropertyAccessor.getProperty("SERVICE_PATH_EXTRACT");
	private static final String SERVICE_PATH_INJECT = PropertyAccessor.getProperty("SERVICE_PATH_INJECT");
	private static final int TIMEOUT = Integer.parseInt(PropertyAccessor.getProperty("TIMEOUT"));

	
	protected static InputStream callWebService(String callType, byte[] requestBytes) throws Exception {
		String SIGNATURE = "callWebService(byte[])";
		HttpURLConnection conn = null;
		InputStream response = null;
		try {
			// Create endpoint
			String endpoint = SERVICE_HOST_PORT;
			if ("INJECT".equals(callType)) {
				endpoint += SERVICE_PATH_INJECT;
			} else {
				endpoint += SERVICE_PATH_EXTRACT;
			}
			
			URL url = new URL(endpoint);
			logMessage(SIGNATURE, "---------------Web Service Call: begin -----------------------");
			logMessage(SIGNATURE, "Endpoint: " + url.toString());
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			
			// Set request header values
			String encoded = Base64.getEncoder().encodeToString((WEB_SERVICE_USER + ":" + WEB_SERVICE_PASS).getBytes(ENCODING));
			conn.setRequestProperty("Authorization", "Basic " + encoded);
			conn.setRequestProperty("Content-Type", "text/xml");
			conn.setRequestProperty("SOAPAction", "http://sap.com/xi/WebService/soap1.1");
			
			// Set timeouts
			conn.setConnectTimeout(TIMEOUT);
			conn.setReadTimeout(TIMEOUT);
						
			// Set request
			logMessage(SIGNATURE, "Setting web service request" );
			setHttpRequest(conn, requestBytes);
			
			// Call service
			logMessage(SIGNATURE, "Calling web service...");
			int status = conn.getResponseCode();
			logMessage(SIGNATURE, "Web service returned HTTP status code: " + status);
			
			// Handle response based on HTTP response code
			if (status == 200) {
				// Success
				logMessage(SIGNATURE, "--> Response is positive...");
				response = conn.getInputStream();
			} else {
				// Error
				response = conn.getErrorStream();
				String resp = Util.inputstreamToString(response, ENCODING);
				logger.writeError(LOCATION, SIGNATURE, "Negative Web Service response (HTTP status code " + status +"): \n" + resp);
				throw new RuntimeException("Error calling web service with endpoint: " + endpoint + ". See the trace for details.");
			}
			
			// Return the web service response
			ByteArrayInputStream bais = new ByteArrayInputStream(response.readAllBytes());
			return bais;
		} catch (Exception e) {
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			String ex = "Error calling web service.\n" + sw.toString();
			logger.writeError(LOCATION, SIGNATURE, ex);
			throw e;
		} finally { 
			if (response != null) {
				response.close();	
			}
			logMessage(SIGNATURE, "---------------Web Service Call: end -----------------------\n");
		}
	}
	
	
	protected static void setHttpRequest(HttpURLConnection con, byte[] requestBytes) {
		try {
			con.setDoOutput(true);
			DataOutputStream dos = new DataOutputStream(con.getOutputStream());
			dos.write(requestBytes);
			dos.flush();
			dos.close();
		} catch (IllegalStateException|IOException e) {
			throw new RuntimeException("*setHttpRequest* Error setting http request.\n" + e);
		}
	}
	
		
	protected static void logMessage(String signature, String msg) {
		if (ENABLE_WS_LOGGING) {
			logger.writeDebug(LOCATION, signature, msg);
		}
	}
}
