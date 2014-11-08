/**
 *   HTTP Server, Multi Threaded
 *   Usage:  java ThreadHTTPServer [port#  [http_root_path]]
 *
 **/

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

class HTTPThread implements Runnable {
	public Socket connectionSocket = null;
	public String http_root_path = null;
	public static Date lastMod;
    public static SimpleDateFormat fmt;

    // constructor to instantiate the HTTPThread object
    public HTTPThread(Socket connectionSocket, String http_root_path) {
    	this.connectionSocket = connectionSocket;
    	this.http_root_path = http_root_path;
    }

    public void run() {
	// invoke processRequest() to process the client request and then generateResponse()
	// to output the response message
    	try {
    		processRequest(connectionSocket);
    	} catch (Exception e) {
    	}
    } 

    private void processRequest(Socket connectionSocket) throws Exception {
	// same as in single-threaded (this code is inline in the starter code)
	// create buffered reader for client input
		BufferedReader inFromClient =  new BufferedReader(new InputStreamReader(connectionSocket.getInputStream())); 

		String requestLine = null;	// the HTTP request line
		String requestHeader = null;	// HTTP request header line

	/* Read the HTTP request line and display it on Server stdout.
	 * We will handle the request line below, but first, read and
	 * print to stdout any request headers (which we will ignore).
	 */
		requestLine = inFromClient.readLine();
		System.out.println("Request Line: " + requestLine);

		requestHeader = inFromClient.readLine();
		while (!requestHeader.equals("")) {
			System.out.println("Request Header: " + requestHeader);
			requestHeader = inFromClient.readLine();
			if (requestHeader.startsWith("If-Modified-Since:")){
				String last = requestHeader.replace("If-Modified-Since: ","");
				fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
				Date lastMod = fmt.parse(last);
			}
		}


	// now back to the request line; tokenize the request
		StringTokenizer tokenizedLine = new StringTokenizer(requestLine);
	// process the request
		if (tokenizedLine.nextToken().equals("GET")) {
		    String urlName = null;	    
	    // parse URL to retrieve file name
	  	  urlName = tokenizedLine.nextToken();
    
	    	if (urlName.startsWith("/") == true )
				urlName  = urlName.substring(1);
	    
	    	generateResponse(urlName, connectionSocket);

		} else 
	    	System.out.println("Bad Request Message");


	}
    

    private void generateResponse(String urlName, Socket connectionSocket) throws Exception {
	// same as in single-threaded
    	// ADD_CODE: create an output stream  
    	DataOutputStream outToClient = new DataOutputStream(connectionSocket.getOutputStream());

		String fileLoc = http_root_path + "/" + urlName; // ADD_CODE: map urlName to rooted path  
		System.out.println ("Request Line: GET " + fileLoc);

		File file = new File( fileLoc );
		if (!file.isFile())
		{
		    // generate 404 File Not Found response header
		    outToClient.writeBytes("HTTP/1.0 404 File Not Found\r\n");
		    // and output a copy to server's stdout
		    System.out.println ("HTTP/1.0 404 File Not Found\r\n");
		} else {
		    // get the requested file content
		    int numOfBytes = (int) file.length();
		    
		    FileInputStream inFile  = new FileInputStream (fileLoc);
		
		    byte[] fileInBytes = new byte[numOfBytes];
		    inFile.read(fileInBytes);

		    // ADD_CODE: generate HTTP response line; output to stdout
		    outToClient.writeBytes("HTTP/1.0 200 OK\n");
		    System.out.println("Response line: HTTP/1.0 200 OK");
		
		    // ADD_CODE: generate HTTP Content-Type response header; output to stdout
		    if (urlName.endsWith(".jpg")){
		    	outToClient.writeBytes("Content-Type: image/jpeg\n");
		    	System.out.println("Response header: Content-Type: image/jpeg\n");
		    } else if (urlName.endsWith(".html")){
		    	outToClient.writeBytes("Content-Type: text/html\n");
		    	System.out.println("Response header: Content-Type: text/html\n");
		    } else if (urlName.endsWith(".css")){
		    	outToClient.writeBytes("Content-Type: text/css\n");
		    	System.out.println("Response header: Content-Type: text/css\n");
		    } else if (urlName.endsWith(".js")){
		    	outToClient.writeBytes("Content-Type: text/js\n");
		    	System.out.println("Response header: Content-Type: text/js\n");
		    } else if (urlName.endsWith(".txt")){
		    	outToClient.writeBytes("Content-Type: text/txt\n");
		    	System.out.println("Response header: Content-Type: text/txt\n");
		    }

		    // ADD_CODE: generate HTTP Content-Length response header; output to stdout

		   	outToClient.writeBytes("Content-Length: " + numOfBytes + "\r\n");
		    System.out.println("Response Header: Content-Length: " + numOfBytes + "\r\n");

		    Date fileDate = new Date(file.lastModified());
		  	if (lastMod ==  fileDate){
		  		outToClient.writeBytes("HTTP/1.0 304 OK\n");
		    	System.out.println("Response line: HTTP/1.0 304 OK");
		  	} else {
		  		outToClient.writeBytes("Last-Modified: " + fileDate + "\r\n");
		    	System.out.println("Response header: Last-Modified: "+ fileDate + "\r\n");	
		  	}

		    outToClient.writeBytes("\r\n\r\n");
		    // send file content
		    outToClient.write(fileInBytes, 0, numOfBytes);
		}  // end else (file found case)

		// close connectionSocket
		connectionSocket.close();
    } // end of generateResponse
    

}

public final class ThreadHTTPServer {

	public static int serverPort = 35130;    // default port CHANGE THIS
    public static String http_root_path = "/csmhome/laihoche/cscd58f14_space/";    // rooted default path in your mathlab area
    
    public static void main(String args[]) throws Exception  {

		if (args.length > 0){
    		serverPort = Integer.parseInt(args[0]);
    		http_root_path = args[1];
    	}

		if (args.length > 2) {
		    System.out.println("usage: java HTTPServer [port_# [http_root_path]]");
		    System.exit(0);
		}

	// ADD_CODE: create server socket 
		ServerSocket serverSocket = null;

		try{
			serverSocket = new ServerSocket(serverPort);
		} catch (Exception e) {
			System.exit(1);
		}

		System.out.println("Listening on port # " + serverPort + " with server path " + http_root_path);

		Socket clientSocket = null;
	
		while (true) {
		    // accept a connection
		    try{
		    	clientSocket = serverSocket.accept();
		    	System.out.println("Connection from " + clientSocket.getInetAddress() + "." + clientSocket.getPort());
		    // Construct an HTTPThread object to process the accepted connection
		    	HTTPThread htt;
		    // Wrap the HTTPThread in a Thread object
		    	htt = new HTTPThread(clientSocket, http_root_path);
		    // Start the thread.
		    	htt.run();
		    } catch (Exception e){

		    }
		}
	
    } 

}