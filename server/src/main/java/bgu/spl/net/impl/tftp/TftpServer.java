package bgu.spl.net.impl.tftp;
import java.io.IOException;
import java.util.function.Supplier;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BaseServer;
import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args){
        Server<byte[]> tpc = Server.threadPerClient(Integer.parseInt(args[0]), ()->new TftpProtocol(),()->new TftpEncoderDecoder());
        tpc.serve();
        try{
            tpc.close(); 
        }
        catch(IOException e){}
    }

}
