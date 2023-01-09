

/**
 * WebServer Class
 * 
 * Implements a multi-threaded web server
 * supporting non-persistent connections.
 * 
 */

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.*;
import java.text.SimpleDateFormat;  
import java.util.Date;  
import java.util.Locale;  
import java.util.concurrent.*;


class Worker extends Thread{

    //Set by constructor.
    Socket workerSocket = null;
    int timeout = 0;


    //Server name
    String ServerName = "Server: Matthew's Own Server.\r\n";
    //Connection type
    String Connection_Type = "Connection: close\r\n\r\n";
    //Date and simple date formatter.
    Date date = new Date();
    SimpleDateFormat formatter = new SimpleDateFormat("EEE, dd MMM yyyy hh:mm:ss zzz"); 
    String strDate = formatter.format(date);
    String Date = "Date: " + strDate + "\r\n";
    

    /**
     *  @param workerSocket         Socket used by worker thread to send and receive responses and data.
     *  @param timeout              Timeout parameter to check if the reponse coming from the client is taking too long.
     */
    public Worker(Socket sock, int timeout){
        workerSocket = sock;
        this.timeout = timeout;
    }

    //This will now receive requests and send objects and responses through the socket.
    public void run(){

        try{
            
            //read incoming request from the socket.
            DataInputStream receive_from_socket = new DataInputStream(workerSocket.getInputStream());
            //Send over Data through this stream
            DataOutputStream send_through_socket = new DataOutputStream(workerSocket.getOutputStream());
            String request = "";
            String [] parsed_Request = new String[3];
            byte[] buffer = new byte[1024];

    //--------------------------------------TIMEOUT ------------------------------------------------//
        
            //if catch change bool timeout to true.
            boolean socket_timeout = false;
            //Set a timer to receive any data. If no data is transmitted at the time then the server will close.
            workerSocket.setSoTimeout(this.timeout);
            try{
                //This \r\n\r\n would indicate that the request has ended.
                while(!request.contains("\r\n\r\n")){
                    char c = (char)receive_from_socket.readByte();
                    request = request + c;
                }
                //Print out the request.
                System.out.println("Request: ");
                System.out.println(request);
                System.out.println("Response: ");
                parsed_Request = request.split(" ");
            }catch(SocketTimeoutException e){
                //if socket does not send in time, then set socket_timeout to true and check.
                socket_timeout = true;
            }

            if(socket_timeout == true){
                String Request_Timeout_Response = "HTTP/1.1 408 Request Timeout\r\n" + Date + ServerName + Connection_Type;
                byte [] timeout_byte = Request_Timeout_Response.getBytes();
                send_through_socket.write(timeout_byte);
                System.out.println(Request_Timeout_Response);
            }
    //--------------------------------------TIMEOUT------------------------------------------------//

    //--------------------------------------BAD REQUEST--------------------------------------------//
            else if(!parsed_Request[0].contains("GET") || !parsed_Request[2].contains("HTTP/1.1")){
                String Bad_Request = "HTTP/1.1 400 Bad Request \r\n" + Date + ServerName + Connection_Type;
                byte [] bad_request_byte = Bad_Request.getBytes();
                send_through_socket.write(bad_request_byte);
                System.out.println(Bad_Request);
            }
    //--------------------------------------BAD REQUEST--------------------------------------------//

    //-------------------------------------------IF NO TIMEOUT AND NO FORMAT ERROR -------------------------------------------------------//
            else{
                //Send an okay message if file found
                String path = parsed_Request[1].substring(1);
                //If just the '/' character then just give back the index.html file.
                if(path.length() <= 1){
                    path = "index.html";
                }
                File f = new File(path);
                //if the file exists
                
                if(f.exists()){
                //------------------------------------------OKAY RESPONSE-------------------------------------------//
                    //------------------------------------------HEADER STRING-------------------------------------------//
                    String OK_response = "HTTP/1.1 200 OK\r\n";        

                    //Last-Modified get it from the file system.      this_file.lastModified()
                    Date d = new Date(f.lastModified());
                    String Last_Modified = "Last-Modified: " + d + "\r\n";

                    //Content Length
                    String file_length = "Content-Length: " + f.length() + "\r\n";

                    //Content Type
                    //Take the path and manipulate it 
                    Path p = Paths.get(path);
                    String content = Files.probeContentType(p);
                    String content_type = "Content-Type: " + content + "\r\n";

                    String header = OK_response + Date + ServerName + Last_Modified + file_length + content_type + Connection_Type;
                    byte[] headerByte = header.getBytes();
                    //Send the okay response to the client as bytes.
                    send_through_socket.write(headerByte);
                    System.out.println(header);
                    
                    //------------------------------------------HEADER STRING-------------------------------------------//

                    //----------------------------------READING FILE/WRITING SOCKET--------------------------------------------//
                    //Now we need to start reading from the file and write to the socket.
                    FileInputStream file = new FileInputStream(f);
                    //Start reading from the file.
                    int count;
                    //Read from file using this.
                    while((count = file.read(buffer)) != -1){
                        //let's then take this and write it to the socket.
                        send_through_socket.write(buffer, 0, count);
                    }
                    //close the file.
                    file.close();
                    //----------------------------------READING FILE/WRITING SOCKET--------------------------------------------//
                }
                //-------------------------------------------OKAY RESPONSE-------------------------------------------------------//

                //-----------------------------------------NOT FOUND RESPONSE----------------------------------------------------//
                else{
                    //We want to send an error to the client that states the file does not exist.
                    String Not_Found_Response = "HTTP/1.1 404 Not Found\r\n" + Date + ServerName + Connection_Type;
                    byte [] byte_array_not_found = Not_Found_Response.getBytes();
                    send_through_socket.write(byte_array_not_found);
                    System.out.println(Not_Found_Response);
                }
                //-----------------------------------------NOT FOUND RESPONSE----------------------------------------------------//
            }
    //-------------------------------------------IF NO TIMEOUT AND NO FORMAT ERROR -------------------------------------------------------//
        //Close the streams.
        receive_from_socket.close();
        send_through_socket.close();
        //Close the sockets.
        workerSocket.close();
        }catch(IOException e){
            System.out.println(e);
        }
    }

}

public class WebServer extends Thread {
	
    //the port number will connect to 1024
    public int port = 0;
    public int timeout = 0;
    private boolean shutdown = false;
    private ExecutorService executor;
	
    /**
     * Constructor to initialize the web server
     * 
     * @param port 	    The server port at which the web server listens > 1024
     * @param timeout 	The timeout value for detecting non-resposive clients, in milli-second units
     * 
     */
	public WebServer(int port, int timeout){
        this.port = port;
        this.timeout = timeout;
        executor = Executors.newFixedThreadPool(8); 
    }

	
    /**
	 * Main web server method.
	 * The web server remains in listening mode 
	 * and accepts connection requests from clients 
	 * until the shutdown method is called.
	 *
     */
	public void run(){
        //Step 1. Create a new server socket.
        try{
            ServerSocket serverSocket = new ServerSocket(this.port);
            Socket socket = null;
            serverSocket.setSoTimeout(100);
            //WHILE NOT SHUT DOWN, KEEP EXECUTING.
            while(shutdown != true){
                try{
                    socket = serverSocket.accept();
                    InetSocketAddress socketAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
                    String clientIpAddress = socketAddress.getAddress().getHostAddress();
                    System.out.println("IP address: " + clientIpAddress + " Port#: " + this.port);
                    Thread t = new Worker(socket, timeout);
                    executor.execute(t);
                    
                }catch(SocketTimeoutException e){
                    //do nothing
                }
            }
            //TRY TO CLOSE THE TASKS FIRST.
            try{
                executor.shutdown();
                if(!executor.awaitTermination(5, TimeUnit.SECONDS)){
                    executor.shutdownNow();
                }
            }catch(InterruptedException e){
                executor.shutdownNow();
            }
                //CLOSE UP REMAINING SOCKETS.
                socket.close();
                serverSocket.close();
        }catch(IOException e){
            //FORCE THREAD SHUTDOWN.
            executor.shutdownNow();
            System.out.println(e);
        }
    }   
    /**
     * Signals the web server to shutdown.
	 *
     */
	public void shutdown(){
        shutdown = true;
    }
	
}
