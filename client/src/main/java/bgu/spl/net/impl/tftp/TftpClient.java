package bgu.spl.net.impl.tftp;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;
public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) {
        System.out.println("client started");
        try{
            Socket sock = new Socket(args[1], Integer.parseInt(args[0]));
            BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream());
            ClientMessageEncoderDecoder encdec = new ClientMessageEncoderDecoder();
            ClinetTftpProtocol protocol = new ClinetTftpProtocol();
            ListeningThread lt = new ListeningThread(out, in, encdec , protocol);
            KeyboardThread kt = new KeyboardThread(out, encdec, protocol);
            lt.start();
            kt.start();
            kt.join();
            lt.join();
            sock.close();
    }
    catch(IOException e){}
    catch(InterruptedException e){}

    }
}
    class ListeningThread extends Thread{
        private BufferedOutputStream out;
        private ClientMessageEncoderDecoder encdec; 
        private ClinetTftpProtocol protocol;
        private BufferedInputStream in;
        private String relativePath = "";
        
        public ListeningThread(BufferedOutputStream out, BufferedInputStream in, ClientMessageEncoderDecoder encdec, ClinetTftpProtocol protocol){
            this.out = out;
            this.encdec = encdec;
            this.protocol = protocol;
            this.in = in;
        }

        public void run(){
            int read;
            try{
                while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                    byte[] nextMessage = encdec.decodeNextByte((byte) read);
                    if (nextMessage != null) {
                        byte[] msg = protocol.process(nextMessage);
                        if(msg!=null){
                            out.write(msg);
                            out.flush();
                        }
                    }
                    if(protocol.toNotify){
                        synchronized(protocol){
                        protocol.notifyAll();
                        protocol.toNotify = false;
                        }
                    }
                }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}


    class KeyboardThread extends Thread{
        private BufferedOutputStream out;
        private ClientMessageEncoderDecoder encdec; 
        private ClinetTftpProtocol protocol;
        private String relativePath = "";
        public KeyboardThread(BufferedOutputStream out, ClientMessageEncoderDecoder encdec, ClinetTftpProtocol protocol){
            this.out = out;
            this.encdec = encdec;
            this.protocol = protocol;
        }
        
        public void run(){
            try(BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in))){
                String line;
                while(!protocol.shouldTerminate() && (line=userInput.readLine())!=null){
                    byte[] encodedMessage = encdec.encode(line.getBytes());
                    if(encodedMessage!=null){
                        boolean toSend = true;
                        if(encodedMessage[0]==0 && encodedMessage[1]==2){
                            byte[] fileName = Arrays.copyOfRange(encodedMessage, 2, encodedMessage.length-1);
                            String fileString = new String(fileName, StandardCharsets.UTF_8);
                            Path p = Paths.get(relativePath+"./"+fileString);
                            System.out.println(p);
                            if(Files.exists(p)){
                                protocol.startSendingData(fileName);
                            }
                            else{
                                toSend = false;
                                System.out.println("File does not exist on client folder.");
                            }
                        }
                        else if(encodedMessage[0]==0 && encodedMessage[1]==1){
                            byte[] fileName = Arrays.copyOfRange(encodedMessage, 2, encodedMessage.length-1);
                            toSend = protocol.startGettingData(fileName);
                        }
                        else if(encodedMessage[0]==0 && encodedMessage[1]==6){
                            protocol.startReceivingDir();
                        }
                        if(toSend){
                            protocol.setStates(encodedMessage[0], encodedMessage[1]);
                            out.write(encodedMessage);
                            out.flush();
                            if(!(!protocol.connected && encodedMessage[0]==0 && encodedMessage[1]==10)){
                            synchronized(protocol){
                            try{
                                protocol.wait();
                            }
                            catch(InterruptedException e){}
                            }
                        }
                        else{
                            protocol.shouldTerminate=true;
                        }
                    }
                }
            }
        }
            catch(IOException e){}
        }
    }


