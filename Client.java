/* Video streaming using RTP/RTSP client application*/

// Java packages
import java.io.*;
import java.net.*;
import java.util.*;
import java.text.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.imageio.ImageIO;
import java.net.URL;

// Class to send rtsp messages and play the video received from the server
public class Client{
    
    static int rtpPort;
    static int INIT = 0, READY = 1, PLAYING = 2;
    static int rtspState;
    static String mediaFile;
    static String newLine = "\r\n";
    
    // RTP over UDP
    DatagramSocket rtpSocket; 
    DatagramPacket rtpPacket;
    
    // RTSP over TCP    
    Socket rtspSocket;
    int packetSequenceNo = 0; 
    int rtspSessionId = 0;
    static BufferedReader rtspReadBuffer;
    static BufferedWriter rtspWriteBuffer;
    
    InetAddress serverIpAddress;
    
    FrameSynchronizer frameSynchronizer;
    
    // Client video player GUI
    JFrame playerFrame = new JFrame("RtspClient");
    JPanel pnlMain = new JPanel();
    JPanel pnlButtons = new JPanel();
   JPanel imm = new JPanel();

    
    // Buttons

    JButton btnSetup = new JButton("Setup");
    JButton btnPlay = new JButton("Play");
    JButton btnFPlay = new JButton("Fast-Play");
    JButton btnRepeat = new JButton("Re-Play");
    JButton btnPause = new JButton("Pause");
    JButton btnTeardown = new JButton("Close");
   // JButton btnDescribe = new JButton("Describe");
    
    //RTCP variables    
    DatagramSocket RTCPsocket;
    static int RTCP_RCV_PORT = 9002;
    static int RTCP_PERIOD = 400;
    RtcpSender rtcpSender;
    
    JLabel lblStat1 = new JLabel();
    JLabel lblStat2 = new JLabel();
    JLabel lblStat3 = new JLabel();
    JLabel iconLabel = new JLabel();
    ImageIcon imgIcon;
    
    Timer timer; 
    byte[] buf; 
   
    //Statistics variables
    double statDataRate;
    int statTotalBytes;
    double statStartTime;
    double statTotalPlayTime;
    float statFractionLost;
    int statCumLost;
    int statExpRtpNb;
    int statHighSeqNb;
    
    public Client() {
        playerFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        
        pnlButtons.setLayout(new GridLayout(1,0));
        pnlButtons.add(btnSetup);
        pnlButtons.add(btnPlay);
        pnlButtons.add(btnPause);
        pnlButtons.add(btnFPlay);
 	pnlButtons.add(btnRepeat);
        pnlButtons.add(btnTeardown);

       // pnlButtons.add(btnDescribe);
        
        // Delegating button click events to handlers
        btnSetup.addActionListener(new BtnSetupEventHandler());
        btnPlay.addActionListener(new BtnPlayEventHandler());
        btnPause.addActionListener(new BtnPauseEventHandler());
        btnTeardown.addActionListener(new BtnTeardownEventHandler());
        btnRepeat.addActionListener(new BtnRepeatEventHandler());
        btnFPlay.addActionListener(new BtnFPlayEventHandler());
       // btnDescribe.addActionListener(new BtnDescribeEventHandler());

        iconLabel.setIcon(null);
        
        pnlMain.setLayout(null);
        pnlMain.add(imm);
        pnlMain.add(pnlButtons);
        
        pnlMain.add(lblStat1);
        pnlMain.add(lblStat2);
        pnlMain.add(lblStat3);
	imm.add(iconLabel);
        imm.setBounds(0,50,500,150);
        
        pnlButtons.setBounds(0,280,500,50);
        
        lblStat1.setBounds(0,330,500,20);
        lblStat2.setBounds(0,350,500,20);
        lblStat3.setBounds(0,370,500,20);
        
        playerFrame.getContentPane().add(pnlMain, BorderLayout.CENTER);
        playerFrame.setSize(new Dimension(500, 500));
        playerFrame.setVisible(true);

        //Statistics
        lblStat1.setText("Total Bytes Received: 0");
        lblStat2.setText("Packets Lost: 0");
        lblStat3.setText("Data Rate (bytes/sec): 0");
        
        //init timer
        timer = new Timer(20, new TimerListener());
        timer.setInitialDelay(0);
        timer.setCoalesce(true);

        //init RTCP packet sender
        rtcpSender = new RtcpSender(400);

        //allocate enough memory for the buffer used to receive data from the server
        buf = new byte[100000];    

        //create the frame synchronizer
        frameSynchronizer = new FrameSynchronizer(100000);
    }

    // Completed
    public static void main(String argv[]) throws Exception
    {
        /*
        if(argv.length<3)
        {
            System.out.println("Usage: java RtspClient <server_ip_address> <server_port> <media_file>");
            System.exit(0);
        }
        */
        String serverAddress = argv[0];
        int serverPort = Integer.parseInt(argv[1]);
	rtpPort = Integer.parseInt(argv[2]);
        mediaFile = argv[3];
        
        Client objRtspClient = new Client();
        objRtspClient.serverIpAddress = InetAddress.getByName("localhost");

        // Socket API
        objRtspClient.rtspSocket = new Socket(objRtspClient.serverIpAddress, serverPort);

        rtspReadBuffer = new BufferedReader(new InputStreamReader(objRtspClient.rtspSocket.getInputStream()));

        rtspWriteBuffer = new BufferedWriter(new OutputStreamWriter(objRtspClient.rtspSocket.getOutputStream()));

        rtspState = INIT;
    }

    // Completed
    // Setup button click event handler class
    class BtnSetupEventHandler implements ActionListener{
        public void actionPerformed(ActionEvent e){
            System.out.println("Setup request raised.");      
            if (rtspState == INIT) {
                try {                    
                    rtpSocket = new DatagramSocket(null);
		    rtpSocket.setReuseAddress(true);
		    rtpSocket.bind(new InetSocketAddress("127.0.0.1", rtpPort));

                    //UDP socket for sending QoS RTCP packets
                    RTCPsocket = new DatagramSocket(null);
		    RTCPsocket.setReuseAddress(true);
                    //set TimeOut value of the socket to 5msec.
                    rtpSocket.setSoTimeout(5);                    
                }
                catch (Exception ex)
                {
                    System.out.println(ex.getMessage());
                    System.exit(0);
                }

                packetSequenceNo = 1;
                SendRtspRequest("SETUP");
 
                if (ReadServerResponse() != 200)
                    System.out.println("Server response is not valid!");
                else 
                {
                    rtspState = READY;
                    System.out.println("Current RTSP state: READY");
                }
            }
        }
    }
    
    // Completed
    // Play button click event handler class
    class BtnPlayEventHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            System.out.println("Play request raised."); 
            
            statStartTime = System.currentTimeMillis();

                packetSequenceNo++;
                SendRtspRequest("PLAY");

                if (ReadServerResponse() != 200) {
                    System.out.println("Server response is not valid!");
                }
                else {
                    rtspState = PLAYING;
                    System.out.println("Current RTSP state: PLAYING");

                    timer.start();
                    rtcpSender.startSend();
                }
        }
    }

    // Completed
    // Pause button click event handler class
    class BtnPauseEventHandler implements ActionListener {
        public void actionPerformed(ActionEvent e){
            System.out.println("Pause request raised.");
            if (rtspState == PLAYING) 
            {
                packetSequenceNo++;
                SendRtspRequest("PAUSE");

                if (ReadServerResponse() != 200)
                    System.out.println("Server response is not valid!");
                else 
                {
                    rtspState = READY;
                    System.out.println("Current RTSP state: READY");
                      
                    timer.stop();
                    rtcpSender.stopSend();
                }
            }            
        }
    }

    // Completed
    // Teardown button click event handler class
    class BtnTeardownEventHandler implements ActionListener {
        public void actionPerformed(ActionEvent e){
            System.out.println("Teardown request raised.");
            
            packetSequenceNo++;
            SendRtspRequest("TEARDOWN");
            
            if (ReadServerResponse() != 200)
                System.out.println("Server response is not valid!");
            else {                
                rtspState = INIT;
                System.out.println("Current RTSP state: Stopped");

                timer.stop();
                rtcpSender.stopSend();

                System.exit(0);
            }
        }
    }


    // Completed
    // Repeat button click event handler class
 
    class BtnRepeatEventHandler implements ActionListener {
        public void actionPerformed(ActionEvent e){
            System.out.println("Pause request raised.");
            if (rtspState == PLAYING) 
            {
                packetSequenceNo++;
                SendRtspRequest("REPEAT");

                if (ReadServerResponse() != 200)
                    System.out.println("Server response is not valid!");
                else 
                {
                    rtspState = PLAYING;
                    System.out.println("Current RTSP state: PLAYING");

                    timer.start();
                    rtcpSender.startSend();
                }
            }            
        }
    }

    // Completed
    // FPlay button click event handler class

    class BtnFPlayEventHandler implements ActionListener {
        public void actionPerformed(ActionEvent e){
            System.out.println("Pause request raised.");
            if (rtspState == PLAYING) 
            {
                packetSequenceNo++;
                SendRtspRequest("FPLAY");

                if (ReadServerResponse() != 200)
                    System.out.println("Server response is not valid!");
                else 
                {
                    rtspState = PLAYING;
                    System.out.println("Current RTSP state: PLAYING");

                    timer.start();
                    rtcpSender.startSend();
                }
            }            
        }
    }



    // Completed
    class BtnDescribeEventHandler implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            System.out.println("Describe request raised."); 

            packetSequenceNo++;
            SendRtspRequest("DESCRIBE");

            if (ReadServerResponse() != 200) {
                System.out.println("Server response is not valid!");
            }
            else {     
                System.out.println("Describe response received!");
            }
        }
    }
    
    // Completed
    private void SendRtspRequest(String requestType)
    {
        try {
            rtspWriteBuffer.write(requestType + " " + mediaFile + " RTSP/1.0" + newLine);

            rtspWriteBuffer.write("CSeq: " + packetSequenceNo + newLine);

            switch(requestType)
            {
                case "SETUP":
                    rtspWriteBuffer.write("Transport: RTP/UDP; client_port: " + rtpPort + newLine );
                    break;
                case "DESCRIBE":
                    rtspWriteBuffer.write("Accept: application/sdp" + newLine);
                    break;
                default:
                    rtspWriteBuffer.write("Session: " + rtspSessionId + newLine);                
            }

            rtspWriteBuffer.flush();
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(0);
        }
    }
    
    // Completed
    private int ReadServerResponse() 
    {
        int replyCode = 0;
        try {
            String status = rtspReadBuffer.readLine();
            System.out.println("Response from Server:");
            System.out.println(status);
          
            StringTokenizer tokens = new StringTokenizer(status);
            tokens.nextToken();
            replyCode = Integer.parseInt(tokens.nextToken());
            
            if (replyCode == 200) {
                String sequenceInfo = rtspReadBuffer.readLine();
                System.out.println(sequenceInfo);
                
                String sessionInfo = rtspReadBuffer.readLine();
                System.out.println(sessionInfo);

                tokens = new StringTokenizer(sessionInfo);
                String token = tokens.nextToken();
                if (rtspState == INIT && token.compareTo("Session:") == 0) {
                    rtspSessionId = Integer.parseInt(tokens.nextToken());
                }
                else if (token.compareTo("Content-Base:") == 0) {
                    String strResponseData;
                    for (int i = 0; i < 6; i++) {
                        strResponseData = rtspReadBuffer.readLine();
                        System.out.println(strResponseData);
                    }
                }
            }
        } catch(Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(0);
        }
      
        return(replyCode);
    }

    // Completed
    // Class to synchronize frames based on sequence numbers
    class FrameSynchronizer {
        private ArrayDeque<Image> imageQueue;
        private int bufferSize;
        private int currentSequenceNo;
        private Image lastImage;

        public FrameSynchronizer(int bufSize) {
            currentSequenceNo = 1;
            bufferSize = bufSize;
            imageQueue = new ArrayDeque<Image>(bufferSize);
        }

        public void addFrame(Image image, int sequenceNo) {
            if (sequenceNo < currentSequenceNo) {
                imageQueue.add(lastImage);
            }
            else if (sequenceNo > currentSequenceNo) {
                for (int i = currentSequenceNo; i < sequenceNo; i++) {
                    imageQueue.add(lastImage);
                }
                imageQueue.add(image);
            }
            else {
                imageQueue.add(image);
            }
        }

        public Image nextFrame() {
            currentSequenceNo++;
            lastImage = imageQueue.peekLast();
            return imageQueue.remove();
        }
    }
    
        class RtcpSender implements ActionListener {

        private Timer rtcpTimer;
        int interval;

        // Stats variables
        private int numPktsExpected;    // Number of RTP packets expected since the last RTCP packet
        private int numPktsLost;        // Number of RTP packets lost since the last RTCP packet
        private int lastHighSeqNb;      // The last highest Seq number received
        private int lastCumLost;        // The last cumulative packets lost
        private float lastFractionLost; // The last fraction lost

        Random randomGenerator;         // For testing only

        public RtcpSender(int interval) {
            this.interval = interval;
            rtcpTimer = new Timer(interval, this);
            rtcpTimer.setInitialDelay(0);
            rtcpTimer.setCoalesce(true);
            randomGenerator = new Random();
        }

        public void run() {
            System.out.println("RtcpSender Thread Running");
        }

        public void actionPerformed(ActionEvent e) {

            // Calculate the stats for this period
            numPktsExpected = statHighSeqNb - lastHighSeqNb;
            numPktsLost = statCumLost - lastCumLost;
            lastFractionLost = numPktsExpected == 0 ? 0f : (float)numPktsLost / numPktsExpected;
            lastHighSeqNb = statHighSeqNb;
            lastCumLost = statCumLost;

            //To test lost feedback on lost packets
            // lastFractionLost = randomGenerator.nextInt(10)/10.0f;

            RTCPpacket rtcp_packet = new RTCPpacket(lastFractionLost, statCumLost, statHighSeqNb);
            int packet_length = rtcp_packet.getlength();
            byte[] packet_bits = new byte[packet_length];
            rtcp_packet.getpacket(packet_bits);

            try {
                DatagramPacket dp = new DatagramPacket(packet_bits, packet_length, serverIpAddress, RTCP_RCV_PORT);
                RTCPsocket.send(dp);
            } catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            } catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }

        // Start sending RTCP packets
        public void startSend() {
            rtcpTimer.start();
        }

        // Stop sending RTCP packets
        public void stopSend() {
            rtcpTimer.stop();
        }
    }
    
    class TimerListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {          
            //Construct a DatagramPacket to receive data from the UDP socket
            rtpPacket = new DatagramPacket(buf, buf.length);

            try {
                //receive the DP from the socket, save time for stats
                rtpSocket.receive(rtpPacket);

                double curTime = System.currentTimeMillis();
                statTotalPlayTime += curTime - statStartTime; 
                statStartTime = curTime;

                //create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rtpPacket.getData(), rtpPacket.getLength());
                int seqNb = rtp_packet.getsequencenumber();

                //this is the highest seq num received

                //print important header fields of the RTP packet received: 
                System.out.println("Got RTP packet with SeqNum # " + seqNb
                                   + " TimeStamp " + rtp_packet.gettimestamp() + " ms, of type "
                                   + rtp_packet.getpayloadtype());

                //print header bitstream:
                rtp_packet.printheader();

                //get the payload bitstream from the RTPpacket object
                int payload_length = rtp_packet.getpayload_length();
                byte [] payload = new byte[payload_length];
                rtp_packet.getpayload(payload);

                //compute stats and update the label in GUI
                statExpRtpNb++;
                if (seqNb > statHighSeqNb) {
                    statHighSeqNb = seqNb;
                }
                if (statExpRtpNb != seqNb) {
                    statCumLost++;
                }
                statDataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
                statFractionLost = (float)statCumLost / statHighSeqNb;
                statTotalBytes += payload_length;
                DecimalFormat formatter = new DecimalFormat("###,###.##");
                lblStat1.setText("Total Bytes Received: " + statTotalBytes);
                lblStat2.setText("Packet Lost Rate: " + formatter.format(statFractionLost));
                lblStat3.setText("Data Rate: " + formatter.format(statDataRate) + " bytes/s");

                //get an Image object from the payload bitstream
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                frameSynchronizer.addFrame(toolkit.createImage(payload, 0, payload_length), seqNb);

                //display the image as an ImageIcon object
                imgIcon = new ImageIcon(frameSynchronizer.nextFrame());
                iconLabel.setIcon(imgIcon);
            }
            catch (InterruptedIOException iioe) {
                System.out.println("Nothing to read");
            }
            catch (IOException ioe) {
                System.out.println(ioe.getMessage());
            }
        }
    }    
}
