package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.MessagingProtocol;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

//----------------------------------------------------------------------------------------------------------------------

//----------------------------------------------------------------------------------------------------------------------

class dataContainer{
    private byte[] bytes ; //start with 1k   
    private int len;
    private int readIndex;

    public dataContainer(){
        bytes = new byte[1 << 10];
        len =0;
        readIndex =0;
    }

    public void copyData(int j, byte[] data) {
        for(int i=j; i<data.length; i++){
            if (len >= bytes.length) {
                bytes = Arrays.copyOf(bytes, len * 2);
            }
            bytes[len++]= data[i];
        }
    }

    public byte[] getData() {
        byte[] output = Arrays.copyOfRange(bytes, 0, len);
        len = 0; 
        return output;
    }

    public byte[] getDataChunk() {
        if(len-readIndex<512){
            readIndex=0;
            len=0;
            return bytes;
        }
        byte[] output = Arrays.copyOfRange(bytes, readIndex, readIndex+512);
        readIndex= readIndex+512;
        return output;
        }
    }


//----------------------------------------------------------------------------------------------------------------------

public class ClinetTftpProtocol implements MessagingProtocol<byte[]>  {
    boolean shouldTerminate = false;
    private boolean sendingData = false;
    private boolean gettingData = false; 
    private boolean receivingDir = false;
    private boolean inWRQ = false;
    private boolean inLOGRQ = false;
    private boolean inFirstRRQ = false;
    private boolean inDELRQ = false;
    private boolean inDisc = false;
    public boolean connected = false;
    FileOutputStream writeFile;
    FileInputStream getFile;
    String relativePath = "";
    String currentFile; 
    dataContainer pendingData = new dataContainer();
    public boolean toNotify = false;
    //private dataContainer pendingData;
    

    @Override
    public byte[] process(byte[] message) {
        short messageType = (short)(((short)message[0] & 0x00ff)<<8|(short)(message[1] & 0x00ff));
        if(messageType==3){
            if(receivingDir) return processReceiveDir(message); 
            else{
                return processGetDATA(message);}
        } //done
        if(messageType==4){return processACK(message);} //done
        if(messageType==9){return processBCAST(message);}
        if(messageType==5){return processERROR(message);}
        return null;
    }

    @Override
    public boolean shouldTerminate() {
        return this.shouldTerminate;
        //throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }

    public void startReceivingDir(){
        this.receivingDir = true;
    }

    private byte[] processReceiveDir(byte[] message){
        pendingData.copyData(6, message);
        short sizeOfData = (short)(((short)message[2])<<8|(short)(message[3] & 0x00ff));
        byte[] ackMsg = new byte[4];
        ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = message[4]; ackMsg[3] = message[5];
        if(sizeOfData<512){
            receivingDir = false;
            byte[] dir = pendingData.getData();
            printDir(dir);
            toNotify = true;
        }
        return ackMsg;
    }


    public void setStates(byte b1, byte b2){
        if(b1==0 && b2==1){
            inFirstRRQ = true;
        }
        else if(b1==0 && b2==2){
            inWRQ = true;
        }
        else if(b1==0 && b2==7){
            inLOGRQ = true;
        }
        else if(b1==0 && b2==8){
            inDELRQ = true;
        }
        else if(b1==0 && b2==10){
            inDisc = true;
        }
    }

    public void printDir(byte[] dir){
        int start = 0;
        int end = 0;
        while(end<dir.length){
            while(end<dir.length && dir[end]!=(byte)0){
                end++;
            }
            byte[] currentFile = Arrays.copyOfRange(dir, start, end);
            String fileName = new String(currentFile, StandardCharsets.UTF_8);
            System.out.println(fileName);
            start = end++;
        }
    }
    
    private byte[] processGetDATA(byte[] message){
        if(inFirstRRQ){
            File newfile = new File(relativePath+"./"+currentFile);
            try{
                this.writeFile = new FileOutputStream(newfile, true); 
            }
            catch(IOException e){}
            this.gettingData = true;
            inFirstRRQ = false;
        }
        try{
            writeFile.write(Arrays.copyOfRange(message, 6, message.length));
        }
        catch(IOException e){}
        short sizeOfData = (short)(((short)message[2])<<8|(short)(message[3] & 0x00ff));
        byte[] ackMsg = new byte[4];
        ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = message[4]; ackMsg[3] = message[5];
        if(sizeOfData<512){
            try{
                writeFile.close();
            }
            catch(IOException e){}
            gettingData = false;
            System.out.println("RRQ "+currentFile+" complete");
            toNotify = true;
        }
        return ackMsg;
    }

    public boolean startGettingData(byte[] filename){
        String fileString = new String(filename, StandardCharsets.UTF_8);
        currentFile = fileString;
        Path p = Paths.get(relativePath+"./"+fileString);
        if(!Files.exists(p)){
            return true;
        }
        else{
            System.out.println("File already exists on client.");
            return false;
        }
    }

    public void startSendingData(byte[] filename){
        String fileStringName = new String(filename, StandardCharsets.UTF_8);
        currentFile = fileStringName;
        try{
            getFile = new FileInputStream(relativePath+"./"+fileStringName);
        }
        catch (IOException e){}
        this.sendingData = true; 
    }

    

    private byte[] processSendDATA(byte[] message){
        byte[] data = new byte[512];
        byte[] finalData = new byte[0];
        try{
            int bytesRead = getFile.read(data);
            if(bytesRead!=-1 && bytesRead<512){
                finalData = Arrays.copyOfRange(data, 0, bytesRead);
                sendingData = false;
            }
            else if(bytesRead!=-1){
                finalData = data;
            }
        }
        catch(IOException e){}
        //creating 2bytes for dataSize
        short size = (short)finalData.length;
        byte[] msg = new byte[size+6];
        byte[] size_bytes = new byte[]{(byte) (size >> 8), (byte) (size & 0x00ff)};
        //creating 2bytes for blockNum+1
        short blockNum = (short)(((short)message[2])<<8|(short)(message[3] & 0x00ff));
        blockNum++;
        byte[] blockNum_bytes = new byte[]{(byte) (blockNum >> 8), (byte) (blockNum & 0x00ff)};
        //creating and sending DATA packet
        msg[0]=0; msg[1]=3; msg[2]=size_bytes[0]; msg[3]=size_bytes[1]; msg[4]= blockNum_bytes[0]; msg[5]= blockNum_bytes[1];  
        for(int i=6; i<msg.length; i++) {msg[i] = finalData[i-6];}
        if(size<512) {
            sendingData=false;
            try{
                getFile.close();
            }
            catch(IOException e){}
        }
        return msg;
    }


    private byte[] processACK(byte[] message){
        short blockNum = (short)(((short)message[2] & 0x00ff)<<8|(short)(message[3] & 0x00ff));
        System.out.println("ACK "+blockNum);
        if(inWRQ && !sendingData){ //in WRQ
            System.out.println("WRQ "+currentFile+" complete");
            inWRQ = false;
            toNotify = true;
        }
        else if(inLOGRQ){
            inLOGRQ = false;
            connected = true;
            toNotify = true;
        }
        else if(inDELRQ){
            inDELRQ = false;
            toNotify = true;
        }
        else if(sendingData){
            return processSendDATA(message);
        }
        else if(inDisc){
            inDisc = false;
            shouldTerminate=true;
            toNotify = true;        
        }
        return null;
    }

    private byte[] processBCAST(byte[] message){
        String filename = new String(message, 3, message.length-3, StandardCharsets.UTF_8);
        if(message[2]==0){
            System.out.println("BCAST del "+filename);
        }
        else{
            System.out.println("BCAST add "+filename);
        }
        return null;
    }

    private byte[] processERROR(byte[] message){
        short errorNum = (short)(((short)message[2] & 0x00ff)<<8|(short)(message[3] & 0x00ff));
        String errorMsg = new String(message, 4, message.length-4, StandardCharsets.UTF_8);
        if(errorNum==1 && gettingData){
            try{
                //need to delete the file from client!!
                this.writeFile.close();
                this.inFirstRRQ = false;
            }
            catch(IOException e){}
        }
        else if(errorNum==5){
            try{
                this.getFile.close();
                this.sendingData = false;
                this.inWRQ = false;
            }
            catch(IOException e){}
            this.sendingData = false;
        }
        System.out.println("Error "+errorNum+" "+errorMsg);
        toNotify = true;
        return null;
    }



}
    





    



        // Wake up handler to continue working with client
       // throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");






    

