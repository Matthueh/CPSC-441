

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.*;
import java.util.Date;  
import java.net.*;
import java.io.*;

// 1. Strictly keep it to sending packets.


class ReceivePacket extends Thread{
	private int previous_sequence_number;
	private int sequence_number;
	DatagramSocket ds;

	/**
	 * 
	 * @param ds				Datagram socket used to receive the
	 * @param sequence_number 	Sequence Number of the packet.
	 */
	public ReceivePacket(DatagramSocket ds, int sequence_number){
		this.ds = ds;
		this.sequence_number = sequence_number;
		this.previous_sequence_number = sequence_number;
	}
	public void run(){
		try{
			while(true){
				byte[] receive = new byte[4];
				DatagramPacket ftp_rec = new DatagramPacket(receive, receive.length);
				ds.receive(ftp_rec);
				//Parse Response.
				FtpSegment ftp_recieve = new FtpSegment(ftp_rec);
				previous_sequence_number = sequence_number;
				sequence_number = ftp_recieve.getSeqNum();
				if(sequence_number > previous_sequence_number){
					System.out.println("ack " + sequence_number);
					break;
				}
			}
		}catch(IOException e){
			System.out.println(e);
		}
	}
	public int getSequenceNumber(){
		return this.sequence_number;
	}
}

class SendPacket extends Thread{

	private FtpSegment ftp;
    private String serverName;
    private int server_port;
	DatagramSocket ds;
	private int Sequence_Number;

	/**
	 * 
	 * @param ds				Datagram socket we will be using to send packets.
	 * @param ftp				ftp segment we will use for the packet. 
	 * @param serverName		Name of the server.
	 * @param server_port		Port of the server.
	 * @param Sequence_Number	Sequence Number of the packet.
	 */

	public SendPacket(DatagramSocket ds, FtpSegment ftp, String serverName, int server_port, int Sequence_Number){
		this.ds = ds;
		this.ftp = ftp;
        this.serverName = serverName;
        this.server_port = server_port;
		this.Sequence_Number = Sequence_Number;
	}
	public void run(){
		try{
			//This is an indicator that the original sent packet has timed out. 	
			//Send a new one.
			DatagramPacket dp = FtpSegment.makePacket(ftp, InetAddress.getByName(serverName), server_port);
			ds.send(FtpSegment.makePacket(ftp, InetAddress.getByName(serverName), server_port));		
			//if we receive an ack then we want to cancel the timer
		}catch(IOException e){
			System.out.println(e);
		}
	}
}

class TimeoutHandler extends TimerTask{
    private FtpSegment ftp;
    private String serverName;
    private int server_port;
	private DatagramSocket ds;
	private int Sequence_Number;
	public static int i = 0;
	/**
	 * Send the specified file to the remote server
	 * 
	 * @param ds				Datagram socket we are using to send the packet.
	 * @param ftp				Segment of data.
	 * @param serverName		IP address of the server.
	 * @param server_port 		Port of the server to send the packets.
	 * @param Sequence_Number	Number of the acknowledgement currently.
	 */
    public TimeoutHandler(DatagramSocket ds, FtpSegment ftp, String serverName, int server_port, int Sequence_Number){
        this.ds = ds;
		this.ftp = ftp;
        this.serverName = serverName;
        this.server_port = server_port;
		this.Sequence_Number = Sequence_Number;
		this.i = 0;
    }
    public void run(){
			// Keep going until the previous acknowledgement is larger than the original on
			if(i == 0){
				//do nothing
				i++;
			}
			else{
				System.out.println("timeout");
				SendPacket send = new SendPacket(ds, ftp, serverName, server_port, Sequence_Number);
				send.start();
				System.out.println("retx " + Sequence_Number);
			}
			
		}
    
}

public class StopWaitFtp {

	private int timeout;
	
	//private static final Logger logger = Logger.getLogger("StopWaitFtp"); // global logger	

	/**
	 * Constructor to initialize the program 
	 * 
	 * @param timeout		The time-out interval for the retransmission timer, in milli-seconds
	 */
	public StopWaitFtp(int timeout){
		this.timeout = timeout;
	}


	/**
	 * Send the specified file to the remote server
	 * 
	 * @param serverName	Name of the remote server
	 * @param serverPort	Port number of the remote server
	 * @param fileName		Name of the file to be trasferred to the rmeote server
	 */
	public void send(String serverName, int serverPort, String fileName){
		//Establish a tcp connection for the handshake
		synchronized(this){
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
				//Previous sequence number to use for checking.
				int previous_sequence_number = Sequence_Number;
				//Handshake completed.



				//Now create the file input stream.
				FileInputStream from_file = new FileInputStream(file);
				int count;
				//get the maximum payload size for the segment.
				byte buffer[] = new byte[FtpSegment.MAX_PAYLOAD_SIZE];
				//Set up the timer.
				Timer timer = new Timer();
				//While not end of the file.
				while((count = from_file.read(buffer)) != -1){
					//Initial send and print the sequence number.
					synchronized(this){
						FtpSegment ftp = new FtpSegment(Sequence_Number, buffer, count);
						Thread s = new SendPacket(ds, ftp, serverName, Server_UDP_Port, Sequence_Number);
						s.start();
						try{
							s.join();
							System.out.println("send " + Sequence_Number);
						}catch(InterruptedException e){
							System.out.println(e);
						}
						//Start the idle receive
						Thread rec = new ReceivePacket(ds, Sequence_Number);
						rec.start();
						//start the timer task
						TimerTask task = new TimeoutHandler(ds, ftp, serverName, Server_UDP_Port, Sequence_Number);
						Date date = new Date();
						timer.scheduleAtFixedRate(task, date, timeout);
						try{
							//make sure receive is completed before going onto the next task.
							rec.join();
						}catch(InterruptedException e){
							System.out.println(e);
						}					
						Sequence_Number++;
						task.cancel();
					}
				}
				//Close streams.
				//Close the socket.
				TCPsocket.close();
				send_through_socket.close();
				receive_from_socket.close();
				ds.close();
				timer.cancel();
				timer.purge();

			}catch(IOException e){
				System.out.println(e);
			}
		}
	}

} // end of class