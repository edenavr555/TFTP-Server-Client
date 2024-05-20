package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessageEncoderDecoder;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;


public class ClientMessageEncoderDecoder implements MessageEncoderDecoder<byte[]>{

    private byte[] bytes = new byte[1 << 10]; //start with 1k
    private int len = 0;
    private short messageType = 0; //1=RRQ, 2=WRQ, 3=DATA, 4=ACK, 5=ERR, 6=DRIQ, 7=LOGRQ, 8=DELRQ, 9=BCAST, 10=DISC
    int sizeOfData = 0;
    private String relativePath = "";

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (len==0){
            pushByte(nextByte);
        }
        else if (len==1){
            pushByte(nextByte);
            messageType = (short)(((short)bytes[0] & 0x00ff)<<8|(short)(bytes[1] & 0x00ff));
        }
        else{
            if(messageType==3){return DATAdecode(nextByte);}
            if(messageType==4){return ACKdecode(nextByte);}
            if(messageType==5){return ERRdecode(nextByte);}
            if(messageType==9){return BCASTdecode(nextByte);}
        }
        return null; //not a line yet
    }

    @Override
    public byte[] encode(byte[] message) {
        String massageString = new String(message, 0, 3, StandardCharsets.UTF_8);
        if(massageString.equals("LOG")) {return encodeLOGRQ(message);}
        if(massageString.equals("DEL")) {return encodeDELRQ(message);}
        if(massageString.equals("RRQ")) {return encodeRRQ(message);}
        if(massageString.equals("WRQ")) {return encodeWRQ(message);}
        if(massageString.equals("DIS")) {return encodeDISC(message);}
        if(massageString.equals("DIR")) {return encodeDIRQ(message);}
        return null;
    }

    private byte[] encodeLOGRQ(byte[] message){
        byte[] username = Arrays.copyOfRange(message, 6, message.length);
        byte[] msg = new byte[username.length+3];
        msg[0]=0; msg[1]=7; msg[msg.length-1]=0;
        for(int i=2; i<msg.length-1; i++){
            msg[i] = username[i-2];
        }
        return msg;
    }

    private byte[] encodeDELRQ(byte[] message){
        byte[] filename = Arrays.copyOfRange(message, 6, message.length);
        byte[] msg = new byte[filename.length+3];
        msg[0]=0; msg[1]=8; msg[msg.length-1]=0;
        for(int i=2; i<msg.length-1; i++){
            msg[i] = filename[i-2];
        }
        return msg;
    }

    public byte[] encodeRRQ(byte[] message){
        byte[] filename = Arrays.copyOfRange(message, 4, message.length);
        byte[] msg = new byte[filename.length+3];
        msg[0]=0; msg[1]=1; msg[msg.length-1]=0;
        for(int i=2; i<msg.length-1; i++){
            msg[i] = filename[i-2];
        }
        return msg;
    }

    public byte[] encodeWRQ(byte[] message){
        byte[] filename = Arrays.copyOfRange(message, 4, message.length);
        byte[] msg = new byte[filename.length+3];
        msg[0]=0; msg[1]=2; msg[msg.length-1]=0;
        for(int i=2; i<msg.length-1; i++){
            msg[i] = filename[i-2];
        }
        return msg;
    }

    public byte[] encodeDISC(byte[] message){
        byte[] msg = new byte[2];
        msg[0]=0; msg[1]=10;
        return msg;
    }

    public byte[] encodeDIRQ(byte[] message){
        byte[] msg = new byte[2];
        msg[0]=0; msg[1]=6;
        return msg;
    }


    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }


    private byte[] DATAdecode(byte nextByte){
        if(len==2) {
            pushByte(nextByte);
            return null;
        }
        else if (len==3) {
            pushByte(nextByte);
            sizeOfData = (short)(((short)bytes[2] & 0x00ff)<<8|(short)(bytes[3] & 0x00ff));
        }
        else if (len<sizeOfData+6){
            pushByte(nextByte);
        }
        if (len==sizeOfData+6){
            byte[] ans = Arrays.copyOfRange(bytes, 0, len);
            len = 0; 
            return ans;
        }
        return null;
    }

    private byte[] ACKdecode(byte nextByte){
        pushByte(nextByte);
        if(len==4){
            byte[] ans = Arrays.copyOfRange(bytes, 0, len);
            len = 0; 
            return ans;
        }
        return null; //not a line yet
    }

    private byte[] ERRdecode(byte nextByte){
        if(len<4){
            pushByte(nextByte);
            return null; //not a line yet
        }
        if(nextByte==0){
            byte[] ans = Arrays.copyOfRange(bytes, 0, len);
            len = 0; 
            return ans;
        }
        pushByte(nextByte);
        return null; //not a line yet    
    }

    private byte[] BCASTdecode(byte nextByte){
        if(len==2){
            pushByte(nextByte);
            return null; //not a line yet
        }
        if(nextByte==0){
            byte[] ans = Arrays.copyOfRange(bytes, 0, len);
            len = 0; 
            return ans;
        }
        pushByte(nextByte);
        return null; //not a line yet    
    }
}


