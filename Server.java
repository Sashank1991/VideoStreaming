
import java.io.*;
import java.net.*;
import java.awt.*;
import java.util.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.imageio.*;
import javax.swing.*;
import javax.imageio.stream.ImageOutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class Server extends JFrame  
{

	int RTSP_dest_port = 0;
	Socket RTSPsocket; //socket used to send/receive RTSP messages
    	JLabel label;

    public Server(){

        //init Frame
        super("Server");
        //GUI:
        label = new JLabel("Send frame #        ", JLabel.CENTER);
        getContentPane().add(label, BorderLayout.CENTER);

    }
          

    public static void main(String[] args) {
        new Server().startServer(args[0]);
    }

    //------------------------------------
    //main
    //------------------------------------
    public void startServer(String port) {


   //create a Server object
        Server theServer = new Server();
                    System.out.println("Waiting for clients to connect...");

        //show GUI:
        theServer.pack();
        theServer.setVisible(true);

        //get RTSP socket port from the command line
        int RTSPport = Integer.parseInt(port);
        theServer.RTSP_dest_port = RTSPport;
        //Initiate TCP connection with the client for the RTSP session
	
	try{
	    ServerSocket listenSocket = new ServerSocket(RTSPport);

       
	int id = 0;
        while (true) {
			try{
                       		theServer.RTSPsocket = listenSocket.accept();
				ClientServiceThread cliThread = new ClientServiceThread(theServer.RTSPsocket,id++);
                		cliThread.start(); 
			}
			catch(IOException ioe) 
            		{ 
                		System.out.println("Exception encountered on accept. Ignoring. Stack Trace :"); 
                		ioe.printStackTrace(); 
            		} 


                    }
	}catch(IOException ioe){
	    System.out.println("Server cant open port.");
	}

    }

}

//***************sashank Changes ******************


    class ClientServiceThread extends Thread{


	    //RTP variables:
	    //----------------
	    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
	    DatagramPacket senddp; //UDP packet containing the video frames

	     InetAddress client_ip_addr; //Client IP address
	    int RTP_dest_port = 0;      //destination port for RTP packets  (given by the RTSP Client)




	    //Video variables:
	    //----------------
	    int imagenb = 0; //image nb of the image currently transmitted
	    VideoStream video; //VideoStream object used to access video frames
	     int JPEG_TYPE = 26; //RTP payload type for MJPEG video
	     int FRAME_PERIOD = 400; //Frame period of the video to stream, in ms
	     int VIDEO_LENGTH = 49; //length of the video in frames

	    byte[] buf;     //buffer used to store the images to send to the client 
	    int sendDelay;  //the delay to send images over the wire. Ideally should be
		            //equal to the frame rate of the video file, but may be 
		            //adjusted when congestion is detected.

	    //RTSP variables
	    //----------------
	    //rtsp states
	    final  int INIT = 0;
	    final  int READY = 1;
	    final  int PLAYING = 2;
	    //rtsp message types
	    final  int SETUP = 3;
	    final  int PLAY = 4;
	    final  int PAUSE = 5;
	    final  int TEARDOWN = 6;
	    final  int DESCRIBE = 7;
	    final  int REPEAT = 8;
	    final  int FPLAY = 9;


	    //input and output stream filters
  	     int state; //RTSP Server state
  	     Socket RTSPsocket; //socket used to send/receive RTSP messages
	     String VideoFileName; //video file requested from the client
	     int RTSP_ID = 123456; //ID of the RTSP session
	    int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
  	     BufferedReader RTSPBufferedReader;
 	     BufferedWriter RTSPBufferedWriter;
	    
	    final static String CRLF = "\r\n";
	  Socket myClientSocket;
	 int clientID;
    	  boolean running = true;
	stream playThread;

  ClientServiceThread(Socket s, int i) {
	       //init Frame
		super("ClientServiceThread");


		//allocate memory for the sending buffer
		buf = new byte[100000]; 
		myClientSocket=s;
    		clientID = i;
    	  }

    	  public void run() {
		System.out.println("clent socket" + myClientSocket);
		System.out.println("Accepted");
    	    System.out.println("Accepted Client : ID - " + clientID + " : Address - "
    	        + myClientSocket.getInetAddress().getHostName());

    	    try {

    	        //Get Client IP address
    	        client_ip_addr= myClientSocket.getInetAddress();  //Client IP address 
		System.out.println("clent ip Address" + client_ip_addr);
    	        //Initiate RTSPstate
		state= INIT; //RTSP Server state == INIT or READY or PLAY

    	        //Set input and output stream filters:
    	       RTSPBufferedReader = new BufferedReader(new InputStreamReader(myClientSocket.getInputStream()) );
    	       RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(myClientSocket.getOutputStream()) );

    	        //Wait for the SETUP message from the client
    	        int request_type;
    	        boolean done = false;
    	        while(!done) {
    	            request_type = parse_RTSP_request(); //blocking
    	    
    	            if (request_type == SETUP) {
    	                done = true;

    	                //update RTSP state
    	                state = READY;
    	                System.out.println("New RTSP state: READY");
    	             
    	                //Send response
    	                send_RTSP_response();
    	             
    	                //init the VideoStream object:
    	                video = new VideoStream(VideoFileName);

    	                //init RTP and RTCP sockets
    	                RTPsocket = new DatagramSocket();
    	               // RTCPsocket = new DatagramSocket(RTCP_RCV_PORT);
    	            }
    	        }

    	        //loop to handle RTSP requests
    	        while(true) {
    	            //parse the request
    	            
		request_type = parse_RTSP_request(); //blocking
	                System.out.println("***************************************");
 System.out.println(request_type);
 System.out.println(state);	    
	                System.out.println("***************************************");	    
            
    	            if ((request_type == PLAY) && (state == READY)) {
			send_RTSP_response();
  			if(running){
    	                //send back response
			playThread = new stream();
			playThread.start();
			}
			else{
			playThread.resume1();
			running=true;
			}
    	                //update state
    	                state = PLAYING;
    	                System.out.println("New RTSP state: PLAYING");
    	            }
    	            else if ((request_type == PAUSE) && (state == PLAYING)) {
			send_RTSP_response();    	                
			//send back response
			playThread.pause1();
			running=false;
    	                //update state
    	                state = READY;
    	                System.out.println("New RTSP state: READY");
    	            }
    	            else if ((request_type == REPEAT) && (state == PLAYING)) {
			send_RTSP_response();    	                
			playThread.Repeat();
    	                state = PLAYING;
    	                System.out.println("New RTSP state: PLAYING");
    	            }
    	            else if ((request_type == FPLAY) && (state == PLAYING)) {
			send_RTSP_response();    	                
			playThread.setSleepTime(100);
    	                state = PLAYING;
    	                System.out.println("New RTSP state: PLAYING");
    	            }
    	            else if ((request_type == PLAY) && (state == PLAYING)) {
			send_RTSP_response();  
  	                System.out.println("***************************************");	                
			playThread.setSleepTime(200);
    	                state = PLAYING;
    	                System.out.println("New RTSP state: PLAYING");
    	            }
    	            else if (request_type == TEARDOWN) {
    	                //send back response
    	                send_RTSP_response();
    	                //close sockets
    	                RTSPsocket.close();
    	                RTPsocket.close();

    	                System.exit(0);
    	            }
    	            else if (request_type == DESCRIBE) {
    	                System.out.println("Received DESCRIBE request");
    	                send_RTSP_describe();
    	            }
    	        }    	    	
    	    	
    	    	
    	    } catch (Exception e) {
    	      e.printStackTrace();
    	    }
    	  }



//******************************************88

class stream extends Thread {
    private boolean keepRunning = false;
    private boolean isPaused = false;
    private boolean isRepeat = false;
    private int seq = 0;
   private int sleepTime=200;
    private int ExtSleepTime=200;
    public void run() {
        keepRunning = true;
        try {
            while (keepRunning) {
 							byte[] frame;
							
							//if the current image nb is less than the length of the video
							if (imagenb < 329) {
							       try{
								    Thread.sleep(sleepTime); 
								}
								catch(InterruptedException e){

								}

							    try {

							    //update current imagenb
							    imagenb++;
								seq++;
							   
								//get next frame to send from the video, as well as its size
								int image_length = video.getnextframe(buf,imagenb);

						  

								//Builds an RTPpacket object containing the frame
								RTPpacket rtp_packet = new RTPpacket(JPEG_TYPE, seq, imagenb*FRAME_PERIOD, buf, image_length);
				
								//get to total length of the full rtp packet to send
								int packet_length = rtp_packet.getlength();

								//retrieve the packet bitstream and store it in an array of bytes
								byte[] packet_bits = new byte[packet_length];
								rtp_packet.getpacket(packet_bits);

						 		System.out.println("length of packet: "+image_length);
								System.out.println("client address"+ client_ip_addr);
								//send the packet as a DatagramPacket over the UDP socket 
								senddp = new DatagramPacket(packet_bits, packet_length, client_ip_addr, RTP_dest_port);
								System.out.println("client address"+ client_ip_addr);
								RTPsocket.send(senddp);
								System.out.println("senddp is sent");
								System.out.println("Send frame #" + imagenb + ", Frame size: " + image_length + " (" + buf.length + ")");
								//print the header bitstream
								rtp_packet.printheader();

								//update GUI
							       // label.setText("Send frame #" + imagenb);
							    }
							    catch(Exception ex) {
								System.out.println("Exception caught: "+ex);
								System.exit(0);
							    }
							}               


						if (isRepeat) {
							imagenb=0;
						    synchronized (this) {
							// wait for resume() to be called
							
							isRepeat = false;
						    }
						}
						if (isPaused) {
						    synchronized (this) {
							// wait for resume() to be called
							wait();
							isPaused = false;
						    }
						}
						    synchronized (this) {
							sleepTime=ExtSleepTime;
						    }

            }
        } catch (Exception ex) {
            // do stuff
        }
    }

    // note that as-is this won't do anything to a paused thread until
    // it is resumed.
    public void stop1() {
        keepRunning = false;

    }

    public void pause1() {
        isPaused = true;
    }
    public void Repeat() {
    	isRepeat = true;
    }
    public void setSleepTime(int val) {
    	ExtSleepTime = val;
    }

    public synchronized void resume1() {
        // notify anybody waiting on "this"
        notify();
    }
}


    //------------------------------------
    //Parse RTSP Request
    //------------------------------------
    private int parse_RTSP_request()
    {
        int request_type = -1;
        try { 
            //parse request line and extract the request_type:
            String RequestLine = RTSPBufferedReader.readLine();
            System.out.println("RTSP Server - Received from Client:");
            System.out.println(RequestLine);

            StringTokenizer tokens = new StringTokenizer(RequestLine);
            String request_type_string = tokens.nextToken();

            //convert to request_type structure:
            if ((new String(request_type_string)).compareTo("SETUP") == 0)
                request_type = SETUP;
            else if ((new String(request_type_string)).compareTo("PLAY") == 0)
                request_type = PLAY;
            else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
                request_type = PAUSE;
            else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
                request_type = TEARDOWN;
            else if ((new String(request_type_string)).compareTo("DESCRIBE") == 0)
                request_type = DESCRIBE;
            else if ((new String(request_type_string)).compareTo("REPEAT") == 0)
                request_type = REPEAT;
            else if ((new String(request_type_string)).compareTo("FPLAY") == 0)
                request_type = FPLAY;


            if (request_type == SETUP) {
                //extract VideoFileName from RequestLine
                VideoFileName = tokens.nextToken();
            }

            //parse the SeqNumLine and extract CSeq field
            String SeqNumLine = RTSPBufferedReader.readLine();
            System.out.println(SeqNumLine);
            tokens = new StringTokenizer(SeqNumLine);
            tokens.nextToken();
            RTSPSeqNb = Integer.parseInt(tokens.nextToken());
        
            //get LastLine
            String LastLine = RTSPBufferedReader.readLine();
            System.out.println(LastLine);

            tokens = new StringTokenizer(LastLine);
            if (request_type == SETUP) {
                //extract RTP_dest_port from LastLine
                for (int i=0; i<3; i++){
                  tokens.nextToken(); //skip unused stuff
		}
		String str= tokens.nextToken().toString();
                RTP_dest_port = Integer.parseInt(str);
		System.out.println( RTP_dest_port);
            }
            else if (request_type == DESCRIBE) {
                tokens.nextToken();
                String describeDataType = tokens.nextToken();
            }
            else {
                //otherwise LastLine will be the SessionId line
                tokens.nextToken(); //skip Session:
                RTSP_ID = Integer.parseInt(tokens.nextToken());
            }
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
      
        return(request_type);
    }

    // Creates a DESCRIBE response string in SDP format for current media
    private String describe() {
        StringWriter writer1 = new StringWriter();
        StringWriter writer2 = new StringWriter();
        
        // Write the body first so we can get the size later
        writer2.write("v=0" + CRLF);
        //writer2.write("m=video " + RTSP_dest_port + " RTP/AVP " + JPEG_TYPE + CRLF);
        writer2.write("a=control:streamid=" + RTSP_ID + CRLF);
        writer2.write("a=mimetype:string;\"video/MJPEG\"" + CRLF);
        String body = writer2.toString();

        writer1.write("Content-Base: " + VideoFileName + CRLF);
        writer1.write("Content-Type: " + "application/sdp" + CRLF);
        writer1.write("Content-Length: " + body.length() + CRLF);
        writer1.write(body);
        
        return writer1.toString();
    }

    //------------------------------------
    //Send RTSP Response
    //------------------------------------
    private void send_RTSP_response() {
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
            RTSPBufferedWriter.write("Session: "+RTSP_ID+CRLF);
            RTSPBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }

    private void send_RTSP_describe() {
        String des = describe();
        try {
            RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
            RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
            RTSPBufferedWriter.write(des);
            RTSPBufferedWriter.flush();
            System.out.println("RTSP Server - Sent response to Client.");
        } catch(Exception ex) {
            System.out.println("Exception caught: "+ex);
            System.exit(0);
        }
    }

}
