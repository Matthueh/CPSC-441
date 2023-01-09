

import java.io.IOException;
import java.util.logging.*;
import java.io.*;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.*;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.*;
import java.net.*;

//TODO list
//First start with the basic implementation
//send packets the size of the window size one after the other 
//and then deal with everything else after


//SEND should be done,
//Things to note
//Accessing the concurrent queue might be wrong. 
//Need to make sure that the receiving acknowledgement is correct.
class SEND extends Thread{
	//We will take in th3e input file and the max window size.
	FileInputStream in_file;
	private int MAX_WINDOW_SIZE;
	DatagramSocket ds;
	String serverName;
	int server_port;
	int rto;
	Timer send_timer;
	TimerTask send_TimerTask;
	private ConcurrentLinkedQueue <FtpSegment> send_window;
	public boolean notify_everyone = false;

	//This section will be constantly changing variables 
	int current_window_size = 0;
	
	//The current acknowledgement will be the acknowledgment coming back from the server.
	int current_ack;

	//create a queue of segments 
	//and keep track of each using the current segment + it's position in the queue
	//drop the segments up to that point 
	//create the same amount of new segments and add them to the queue

	public SEND(FileInputStream in_file, int MAX_WINDOW_SIZE, int current_ack, DatagramSocket ds, String serverName, int server_port, int rto, Timer send_timer, 
																					TimerTask send_TimerTask, ConcurrentLinkedQueue <FtpSegment> send_window){
		this.in_file = in_file;
		this.MAX_WINDOW_SIZE = MAX_WINDOW_SIZE;
		this.current_ack = current_ack;
		this.ds = ds;
		this.serverName = serverName;
		this.server_port = server_port;
		this.rto = rto;
		this.send_timer = send_timer;
		this.send_TimerTask = send_TimerTask;
		this.send_window = send_window;
	}


	public void run(){
		//keep running until the file is not empty
		try{ 
			//keep going until the file runs out.
			int count;
			//While not end of file.
			byte [] buffer = new byte[FtpSegment.MAX_PAYLOAD_SIZE];
			while((count = in_file.read(buffer)) != -1){
				//Create segment.
				FtpSegment ftp = new FtpSegment(this.current_ack, buffer, count);
				//if transmission queue is full
				if(MAX_WINDOW_SIZE == send_window.size()){
					//Tell the thread to wait.
					try{
						wait();	
					}catch(InterruptedException e){
						System.out.println(e);
					}			
				}
				
				//If the windowsize is smaller than the MAX WINDOW SIZE
				//Send the segment.
				ds.send(FtpSegment.makePacket(ftp, InetAddress.getByName(serverName), server_port));
				//Add the segment to queue.
				send_window.offer(ftp);
				//Check if the segment is first in the queue.
				if(current_ack == send_window.peek().getSeqNum()){
					//If it is first in queue, then start timer.
					System.out.println("first");
					Date date = new Date();
					Timer timer_send = new Timer();
					TimerTask timer_Task_Send = new TimeoutHandler(ds, serverName, server_port, send_window);
					timer_send.scheduleAtFixedRate(timer_Task_Send, date, rto);
					timer_send.cancel();
				}
				//Get the current ack and add the size of the payload
				//This may or may not work.
				this.current_ack++;
			}
			//Once this while loop is finished we need to notify everyone else 
			notify_everyone = true;
		}catch(IOException e){
			System.out.println(e);
		}

	}
	public boolean notify_everyone(){
		return notify_everyone;
	}
}


//If the first packet is received 
//cancel timer and pop out the queue
class RECEIVE extends Thread{

	//implement a queue here 
	//implement a 
	private int expected_ack;
	DatagramSocket ds;
	private boolean break_free = false;
	Timer receive_timer;
	TimerTask receive_TimerTask;
	int rto;
	ConcurrentLinkedQueue <FtpSegment> receive_window;
	public boolean finished = false;
	String serverName;
	int server_port;

	public RECEIVE(int expected_ack, DatagramSocket ds, String serverName, int server_port, int rto, Timer receive_timer, TimerTask receive_TimerTask, ConcurrentLinkedQueue <FtpSegment> receive_window){
		this.expected_ack = expected_ack;
		this.ds = ds;
		//pass in the timer object.
		this.receive_timer = receive_timer;
		this.serverName = serverName;
		this.server_port = server_port;
		//pass in timer task object as well.
		this.rto = rto;
		this.receive_TimerTask = receive_TimerTask;
		this.receive_window = receive_window;
	}

	public void run(){
		//Keep receiving with the current acknowledgement.
		//If the acknowledgement that we are waiting for is found, then we want to increment the receive the function.
		//notify when to break this possibly by calling a bool 
		try{
			while(!break_free && receive_window.isEmpty()){
				//receive the acknowledgement.
				byte [] receive_ack = new byte[4];
				DatagramPacket ftp_rec = new DatagramPacket(receive_ack, receive_ack.length);
				ds.receive(ftp_rec);

				
				//Parse out the acknowledgement.
				FtpSegment ftp_receive = new FtpSegment(ftp_rec);
				System.out.println(ftp_receive.getSeqNum());
				//check if acknowledgement is valid.
				//System.out.println(ftp_receive);
				if(ftp_receive.getSeqNum() > expected_ack){
					//If it is valid
					//Then set the expected acknowledgement to the segment that just came in.
					expected_ack = ftp_receive.getSeqNum();
					//Stop the timer
					receive_TimerTask.cancel();
					//Update the transmission queue
					synchronized(this){
						int to_Start = 0;
						Object [] Array = receive_window.toArray();
						FtpSegment [] ftpArray = new FtpSegment[Array.length];
						for(int i = 0; i < Array.length; i++){
							ftpArray[i] = (FtpSegment)Array[i];
						}
						//go through the queue and check which ones are larger
						for(int i = 0; i < ftpArray.length ; i++){
							if(ftpArray[i].getSeqNum() < expected_ack){
								//do nothing
								to_Start++;
							}
						}
						//now create a new list.
						FtpSegment [] newArray = new FtpSegment[ftpArray.length - to_Start];
						for(int i = 0, j = to_Start ; i < newArray.length ; i++, j++){
							newArray[i] = ftpArray[j];
						}
						//new Array is now filled with the values
						//create a new concurrent list
						while(!this.receive_window.isEmpty()){
							this.receive_window.poll();
						}
						//now add all elements back to the concurrent list
						for(int i = 0; i < newArray.length; i++){
							this.receive_window.add(newArray[i]);
						}
					}
					//should be refilled up.
					//start the timer.
					Date date = new Date();
					Timer rec_timer = new Timer();
					TimerTask rec_timer_task = new TimeoutHandler(ds, serverName, server_port, receive_window);
					rec_timer.schedule(rec_timer_task, date, rto);

					if(!receive_window.isEmpty()){
							//start the timer.	
						rec_timer.cancel();
						rec_timer.schedule(rec_timer_task, date, rto);
					}
					//notify the send thread.
					notifyAll();	
					//end of synchronization block.
					rec_timer.cancel();
				}
				
			}
		}catch(IOException e){
			System.out.println(e);
		}
		this.finished = true;
	}

	public void break_free(){
		this.break_free = true;
	}

}


//This should be in charge of just timing out and resending all the packets.
//The timeout should be good
class TimeoutHandler extends TimerTask{
	private ConcurrentLinkedQueue <FtpSegment> send_window;
	private DatagramSocket ds;
	private String serverName;
	private int server_port;
	public TimeoutHandler(DatagramSocket ds, String serverName, int server_port, ConcurrentLinkedQueue <FtpSegment> send_window){
		//pass in the concurrent queue
		this.ds = ds;
		this.serverName = serverName;
		this.server_port = server_port;
		this.send_window = send_window;
	}
	public void run(){
		System.out.println("Timeout has occured resending all the packets...");
		//resend all the packets.
		synchronized(this){
			try{
				if(!send_window.isEmpty()){
					FtpSegment [] ftpArray = null;
					send_window.toArray(ftpArray);
					for(int i = 0; i < ftpArray.length ; i++){
						ds.send(FtpSegment.makePacket(ftpArray[i], InetAddress.getByName(serverName), server_port));
					}
				}
			}catch(IOException e){
				System.out.println(e);
			}
		}
	}
}

public class GoBackFtp {
	// global logger	
	private static final Logger logger = Logger.getLogger("GoBackFtp");
	
	private int windowSize;
	private int rtoTimer;
	/**
	 * Constructor to initialize the program 
	 * 
	 * @param windowSize	Size of the window for Go-Back_N in units of segments
	 * @param rtoTimer		The time-out interval for the retransmission timer
	 */
	public GoBackFtp(int windowSize, int rtoTimer){
		this.windowSize = windowSize;
		this.rtoTimer = rtoTimer;
	}


	/**
	 * Send the specified file to the specified remote server
	 * 
	 * @param serverName	Name of the remote server
	 * @param serverPort	Port number of the remote server
	 * @param fileName		Name of the file to be trasferred to the rmeote server
	 */
	public void send(String serverName, int serverPort, String fileName){
		try{
			//Open TCPsocket.
			Socket TCPsocket = new Socket(serverName, serverPort);
			//Open UDP socket.
			DatagramSocket ds = new DatagramSocket();
			//Set up Data Streams.
			DataOutputStream send_through_socket = new DataOutputStream(TCPsocket.getOutputStream());
			DataInputStream receive_from_socket = new DataInputStream(TCPsocket.getInputStream());
			//Open file.
			File file = new File(fileName);
			//Send the local UDP port number used for file transfer as an int value
			int UDP_port = ds.getLocalPort();
			send_through_socket.writeInt(UDP_port);
			//Send the name of the file.
			send_through_socket.writeUTF(fileName);
			//Send the length (in bytes) of the file as a long
			long length_file = file.length();
			send_through_socket.writeLong(length_file);
			//flush out the output stream.
			send_through_socket.flush();
			//Receive one udp integer port.
			int Server_UDP_Port = receive_from_socket.readInt();
			//Receive the initial sequence number used by the server as an int value.
			int Sequence_Number =receive_from_socket.readInt();
			//Handshake completed.
			
			
			System.out.println("Hand Shake is completed.\n");
			FileInputStream from_file = new FileInputStream(file);
			//Create an instance of the concurrent linked queue
			ConcurrentLinkedQueue <FtpSegment> window = new ConcurrentLinkedQueue<FtpSegment>();
			Timer timer = new Timer();
			TimerTask task = new TimeoutHandler(ds, serverName, Server_UDP_Port, window);
			//Start receiving thread
			RECEIVE r = new RECEIVE(Sequence_Number, ds, serverName, Server_UDP_Port, rtoTimer, timer, task, window);
			Thread rec = r;
			rec.start();
			//Start sending thread
			SEND s = new SEND(from_file, windowSize, Sequence_Number, ds, serverName, Server_UDP_Port, rtoTimer, timer, task, window);
			Thread se = s;
			se.start();
			//Start timer
			Date date = new Date();
			timer.schedule(task, date, rtoTimer);
			//wait until send is done.
			while(s.notify_everyone()){
				//wait
			}
			//once this breaks out, send is complete
			//stop receive.
			r.break_free();
			while(r.finished == false){
				//wait
			}
			//cancel timer.
			//close all the sockets and input streams.
			from_file.close();
			receive_from_socket.close();
			send_through_socket.close();
			ds.close();
			TCPsocket.close();

		}catch(IOException e){
			System.out.println(e);
		}
	}
	
} // end of class