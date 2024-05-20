package bgu.spl.net.impl.tftp;

import java.util.concurrent.ConcurrentHashMap;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
//----------------------------------------------------------------------------------------------------------------------
import java.io.IOException;

class holder{
    static ConcurrentHashMap<String, Integer> namesToIds = new ConcurrentHashMap<>();
    static ConcurrentHashMap<Integer, String> ids = new ConcurrentHashMap<>();
    //static ConcurrentHashMap<byte[], Boolean> files = new ConcurrentHashMap<>();
}
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
            byte[] output = Arrays.copyOfRange(bytes, 0, len);
            readIndex=0;
            len=0;
            return output;
        }
        byte[] output = Arrays.copyOfRange(bytes, readIndex, readIndex+512);
        readIndex= readIndex+512;
        return output;
        }

    public void eraseLastByte(){
        len--;
    }
}


//----------------------------------------------------------------------------------------------------------------------

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {
    boolean shouldTerminate = false;
    private Connections<byte[]> connections;
    private int connectionId;
    private boolean sendingData; //DO WE NEED RECIEVING?
    private byte[] pendingFileName;
    private dataContainer pendingData;
    FileOutputStream writeFile;
    FileInputStream getFile;
    boolean sendingDir;
    boolean connected; 
    String filesPath = "Files";
    

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connectionId = connectionId;
        this.connections = connections;
        this.sendingData = false;
        this.sendingDir = false;
        this.pendingData = new dataContainer();
        this.connected = false;
        //throw new UnsupportedOperationException("Unimplemented method 'start'");
    }

    @Override
    public void process(byte[] message) {
        short messageType = (short)(((short)message[0])<<8|(short)(message[1] & 0x00ff));
        if(messageType==10){processDisc(message);}
        else if(messageType==7){
            if(connected){
                byte[] errMsg = "Client already logged in.".getBytes(StandardCharsets.UTF_8);
                byte[] msg = new byte[errMsg.length+5];
                msg[0] = 0; msg[1] = 5; msg[2] = 0; msg[3] = 0; msg[msg.length-1] = 0;
                for(int i=4; i<msg.length-1; i++){
                    msg[i] = errMsg[i-4];
            }
            this.connections.send(connectionId, msg);
            }
            else
            {
                processLogIn(message);
            }
        }
        else if(!connected){processErrorNotLogin();}
        else if(messageType==1){procesRRQ(message);}
        else if(messageType==2){processWRQ(message);}
        else if(messageType==3){processGetDATA(message);}
        else if(messageType==4){processACK(message);}
        else if(messageType==6){processDIRQ(message);}
        else if(messageType==8){processDelRQ(message);}
        
    }

    @Override
    public boolean shouldTerminate() {
        return this.shouldTerminate;
        //throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }

    private void processLogIn(byte[] message){
        byte[] username = Arrays.copyOfRange(message, 2, message.length);
        String finalname = new String(username, StandardCharsets.UTF_8);
        if(!holder.namesToIds.containsKey(finalname)){
            holder.namesToIds.put(finalname, this.connectionId);
            holder.ids.put(this.connectionId, finalname);
            byte[] ackMsg = new byte[4];
            ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = 0; ackMsg[3] = 0;
            connected = true;
            this.connections.send(connectionId, ackMsg);
        }
        else{
            byte[] errMsg = "Username already exists.".getBytes(StandardCharsets.UTF_8);
            byte[] msg = new byte[errMsg.length+5];
            msg[0] = 0; msg[1] = 5; msg[2] = 0; msg[3] = 7; msg[msg.length-1] = 0;
            for(int i=4; i<msg.length-1; i++){
                msg[i] = errMsg[i-4];
            }
            this.connections.send(connectionId, msg);
        }
    }

    private void processErrorNotLogin(){
        byte[] errMsg = "User not logged in.".getBytes(StandardCharsets.UTF_8);
            byte[] msg = new byte[errMsg.length+5];
            msg[0] = 0; msg[1] = 5; msg[2] = 0; msg[3] = 6; msg[msg.length-1] = 0;
            for(int i=4; i<msg.length-1; i++){
                msg[i] = errMsg[i-4];
            }
            this.connections.send(connectionId, msg);
    }

    private void processDelRQ(byte[] message){
        byte[] filename = Arrays.copyOfRange(message, 2, message.length);
        String fileStringName = new String(filename, StandardCharsets.UTF_8);
        Path p = Paths.get(filesPath+"/"+fileStringName);
        if(Files.exists(p)){
            try{
                Files.delete(p);
            }
            catch(IOException e) {}
            byte[] ackMsg = new byte[4];
            ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = 0; ackMsg[3] = 0;
            this.connections.send(this.connectionId, ackMsg);
            sendBCAST((byte)0, filename);
        }
        else{
            byte[] errMsg = "File not found.".getBytes(StandardCharsets.UTF_8);
            byte[] msg = new byte[errMsg.length+5];
            msg[0] = 0; msg[1] = 5; msg[2] = 0; msg[3] = 1; msg[msg.length-1] = 0;
            for(int i=4; i<msg.length-1; i++){
                msg[i] = errMsg[i-4];
            }
            this.connections.send(connectionId, msg);
        }
        //handle file delete from directory 
    }

    private void processDisc(byte[] message){
        if(connected){
           String name = holder.ids.get(this.connectionId);
            holder.ids.remove(this.connectionId);
            holder.namesToIds.remove(name);
            byte[] ackMsg = new byte[4];
            ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = 0; ackMsg[3] = 0;
            this.connections.send(connectionId, ackMsg);
        }
        this.shouldTerminate = true;
        this.connections.disconnect(connectionId);
    }

    private void processWRQ(byte[] message){
        byte[] fileName = Arrays.copyOfRange(message, 2, message.length);
        String fileStringName = new String(fileName, StandardCharsets.UTF_8);
        Path p = Paths.get(filesPath+"/"+fileStringName);
        if(Files.exists(p)){
            byte[] errMsg = "File already exists.".getBytes(StandardCharsets.UTF_8);
            byte[] msg = new byte[errMsg.length+5];
            msg[0] = 0; msg[1] = 5; msg[2] = 0; msg[3] = 5; msg[msg.length-1] = 0;
            for(int i=4; i<msg.length-1; i++){
                msg[i] = errMsg[i-4];
            }
            this.connections.send(connectionId, msg);
        }
        else{
            String filenameString = new String(fileName, 0, fileName.length, StandardCharsets.UTF_8);
            this.pendingFileName = fileName;
            //maybe need to put a lock
            File newfile = new File(filesPath+"/"+filenameString);
            byte[] ackMsg = new byte[4];
            ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = 0; ackMsg[3] = 0;
            try{
                this.writeFile = new FileOutputStream(newfile, true);
            }
            catch(IOException e){}
            this.connections.send(connectionId, ackMsg);
        }
    }

    private void processGetDATA(byte[] message){
        try{
            writeFile.write(Arrays.copyOfRange(message, 6, message.length));
        }
        catch(IOException e){}
        short sizeOfData = (short)(((short)message[2])<<8|(short)(message[3] & 0x00ff));
        byte[] ackMsg = new byte[4];
        ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = message[4]; ackMsg[3] = message[5];
        this.connections.send(connectionId, ackMsg);
        if(sizeOfData<512){
            try{
                writeFile.close();
            }
            catch(IOException e){}
            sendBCAST((byte)1,pendingFileName);
        }
    }

    private void procesRRQ(byte[] message){
        byte[] fileName = Arrays.copyOfRange(message, 2, message.length);
        String fileStringName = new String(fileName, StandardCharsets.UTF_8);
        Path p = Paths.get(filesPath+"/"+fileStringName);
        if(!Files.exists(p)){
            byte[] errMsg = "File does not exists.".getBytes(StandardCharsets.UTF_8);
            byte[] msg = new byte[errMsg.length+5];
            msg[0] = 0; msg[1] = 5; msg[2] = 0; msg[3] = 1; msg[msg.length-1] = 0;
            for(int i=4; i<msg.length-1; i++){
                msg[i] = errMsg[i-4];
            }
            this.connections.send(connectionId, msg);
        }
        else{
            String fileNameString = new String(fileName, StandardCharsets.UTF_8);
            try{
                getFile = new FileInputStream(filesPath+"/"+fileNameString);
            }
            catch (IOException e){}
            sendingData = true; 
            byte[] ackMsg = new byte[4];
            ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = 0; ackMsg[3] = 0;
            processSendDATA(ackMsg);
        }
    }

    private void processSendDATA(byte[] message){
        byte[] data = new byte[512];
        byte[] finalData = new byte[0];
        try{
            int bytesRead = getFile.read(data);
            if(bytesRead!=-1 && bytesRead<512){
                finalData = Arrays.copyOfRange(data, 0, bytesRead);
            }
            else if(bytesRead!=-1){
                finalData = data;
            }
        }
        catch(IOException e){}
        //creating 2bytes for dataSize
        short size = (short)finalData.length;
        byte[] msg = new byte[size+6];
        byte[] size_bytes = new byte[]{(byte) (size >> 8), (byte) (size & 0xff)};
        //creating 2bytes for blockNum+1
        short blockNum = (short)(((short)message[2])<<8|(short)(message[3] & 0x00ff));
        blockNum++;
        byte[] blockNum_bytes = new byte[]{(byte) (blockNum >> 8), (byte) (blockNum & 0xff)};
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
        this.connections.send(connectionId, msg);
    }


    private void processSendDIR(byte[] message){
        byte[] data = pendingData.getDataChunk();
        //creating 2bytes for dataSize
        short size = (short)data.length;
        byte[] msg = new byte[size+6];
        byte[] size_bytes = new byte[]{(byte) (size >> 8), (byte) (size & 0xff)};
        //creating 2bytes for blockNum+1
        short blockNum = (short)(((short)message[2])<<8|(short)(message[3] & 0x00ff));
        blockNum++;
        byte[] blockNum_bytes = new byte[]{(byte) (blockNum >> 8), (byte) (blockNum & 0xff)};
        //creating and sending DATA packet
        msg[0]=0; msg[1]=3; msg[2]=size_bytes[0]; msg[3]=size_bytes[1]; msg[4]= blockNum_bytes[0]; msg[5]= blockNum_bytes[1];  
        for(int i=6; i<msg.length-1; i++) {msg[i] = data[i-6];}
        if(size<512) {sendingDir=false;}
        this.connections.send(connectionId, msg);
    }

    private void sendBCAST(byte i, byte[] fileName){
        byte[] msg = new byte[fileName.length+4];
        msg[0] = 0; msg[1] = 9; msg[2] = i; msg[msg.length-1] = 0;
        for(int j=3; j<msg.length-1; j++){
            msg[j] = fileName[j-3];
        }
        for(int connectionId: holder.ids.keySet()){
            this.connections.send(connectionId, msg);
        }
    }

    private void processACK(byte[] message){
        if(sendingData){processSendDATA(message);}
        else if(sendingDir){processSendDIR(message);}
    }

    private void processDIRQ(byte[] message){
        byte[] zero = new byte[1]; 
        zero[0]= (byte)0;
        File folder = new File(filesPath);
        if(folder.isDirectory()){
            File[] files = folder.listFiles();
            if(files!=null){
                for(File f: files){
                    pendingData.copyData(0, f.getName().getBytes());
                    pendingData.copyData(0, zero);
                }
            }
        }
        //pendingData.eraseLastByte();
        sendingDir = true;
        byte[] ackMsg = new byte[4];
        short block = 0;
        byte[] blocknum = new byte[]{(byte) (block >> 8), (byte) (block & 0x00ff)};
        ackMsg[0] = 0; ackMsg[1] = 4; ackMsg[2] = blocknum[0]; ackMsg[3] = blocknum[1];
        processSendDIR(ackMsg);
    }
}

    





    



        // Wake up handler to continue working with client
       // throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");






    

