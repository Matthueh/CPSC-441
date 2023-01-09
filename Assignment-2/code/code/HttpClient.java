
/**
 * HttpClient Class
 * 
 * CPSC 441
 * Assignment 2
 * 
 */


import java.util.logging.*;
import java.net.*;
import java.io.*;



public class HttpClient {

	//private static final Logger logger = Logger.getLogger("HttpClient"); // global logger

    /**
     * Default no-arg constructor
     */
	public HttpClient() {
		// nothing to do!
	}
	
    /**
     * Downloads the object specified by the parameter url.
	 *
     * @param url	URL of the object to be downloaded. It is a fully qualified URL.
     */
	public void get(String url){

        //Count the backslashs.
        //Count Indexes.
        System.out.println(url);
        int count = 0;
        int index = 0;
        //Specify Default Port.
        int port = 80;
        while(count < 3 && index < url.length()){
            if(url.charAt(index) == '/'){
                count++;
            }
            index++;
        }
        index--;
        //Parse the ip_address so that the http beginning isn't included
        //and the index of where the object is located isn't include.
        String ip_address = url.substring(7, index);
        //The object String is where the object is located on the URL.
        String object = url.substring(index);

        //If ip_address has port number attached to it, then parse it out reattach the ip_address. A
        //Also change the Port number if necessary.
        if(ip_address.contains(":")){
            index = 0;
            String temporary = "";
            while(ip_address.charAt(index) != ':'){
                index++;
            }
            index++;
            temporary = ip_address.substring(index);
            //Turn it into an integer
            port = Integer.parseInt(temporary);
            ip_address = ip_address.substring(0, index - 1);
        }

        //We need to parse the last part of the file so that we know where to place the output bytes to the file.
        index = 0;
        int temp_index = 0;
        //Find the index of the last slash 
        while(index < url.length()){
            if(url.charAt(index) == '/'){
                temp_index = index + 1;
            }
            index++;
        }
        String outputFile = url.substring(temp_index);

    
        //If the string is empty. Then just show the index.html
        if(object.length() == 1){
            object = "/index.html";
            outputFile = "index.html";
        }

        try{
            //Open the socket.
            System.out.println(ip_address);
            Socket socket = new Socket(ip_address, port);

            //Create a string that emulates an http GET request.

            String HttpRequest;
            HttpRequest = "GET " + object + " HTTP/1.1\r\n" +
                          "\r\n";
            //Print out the HTTP request.
            System.out.println("\nHTTP REQUEST: \n" + HttpRequest + "\n");

            //Using DataOutputStream to send http request over the socket.
            DataOutputStream send_to_socket = new DataOutputStream(socket.getOutputStream());
            send_to_socket.flush();
            //Turn it into bytes and write it into the socket.
            send_to_socket.write(HttpRequest.getBytes("US-ASCII"));


            //Now receive the server response in a stream of bytes.
            DataInputStream receive_from_socket = new DataInputStream(socket.getInputStream());
            //String response will be used for the header response.
            String response = "";
            //String length will be used for the Content-length that will eventually get turned into an integer.
            String length = "";
            //If the response contains "\r\n\r\n" then that will denote the end of the header.
            while(!response.contains("\r\n\r\n")){
                char c = (char)receive_from_socket.readByte();
                if(response.contains("Content-Length: ")){
                    length = length + c;
                }
                response = response + c;
            }
            //Print out the HTTP response.
            System.out.println("HTTP RESPONSE: \n" + response);

            //Parse out the length
            index = 0;
            while(Character.isDigit(length.charAt(index))){
                index++;
            }
            //turn length into number of bytes.
            length = length.substring(0, index);
            int byte_length = Integer.parseInt(length);

            //Make sure the response is "OK" in header line
            if(response.contains("OK")){

                //DataOutputStream with the FileOutputStream to write to the file.
                FileOutputStream output = new FileOutputStream(outputFile);
                DataOutputStream out = new DataOutputStream(output);

                //What we will use to decrement.
                int i = 64;
                //Reset byte array
                byte byte_array[] = new byte[i];
                //Keep decrementing until bytes left are 0 or > 64.
                while(byte_length >= i){
                    receive_from_socket.read(byte_array);
                    byte_length = byte_length - i;
                    out.write(byte_array);
                    //Reset byte array
                    byte_array = new byte[i];
                }
                //Clean up what's left of the bytes.
                byte b [] = new byte[byte_length];
                receive_from_socket.read(b);
                out.write(b);
                //Close DataOutputStream.
                out.close();
                //Close FileOutputStream.
                output.close();
            }

            //Close DataInputStream.
            receive_from_socket.close();
            //Close Socket.
            socket.close();
        }catch(IOException e){
            System.out.println(e);
        }
    }

}
