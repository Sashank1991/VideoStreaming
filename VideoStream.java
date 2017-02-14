//VideoStream

import java.io.*;
import java.awt.image.BufferedImage;


public class VideoStream {

    FileInputStream fis; //video file
    int frame_nb; //current frame nb

    public VideoStream(String filename) throws Exception{
        frame_nb = Integer.parseInt(filename);
    }

    public int getnextframe(byte[] frame, int i) throws Exception
    {  
	int framenum=  frame_nb+i;

 System.out.println("getting frame"+framenum );
        fis = new FileInputStream("./screenshot/"+framenum+".jpg");
        return(fis.read(frame));

    }
}
