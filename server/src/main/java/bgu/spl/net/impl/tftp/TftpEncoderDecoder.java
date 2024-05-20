package bgu.spl.net.impl.tftp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private byte[] bytes = new byte[1 << 10]; //start with 1k
    private int len = 0;
    private short messageType = 0; //1=RRQ, 2=WRQ, 3=DATA, 4=ACK, 5=ERR, 6=DRIQ, 7=LOGRQ, 8=DELRQ, 9=BCAST, 10=DISC
    int sizeOfData = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        if (len==0){
            pushByte(nextByte);
        }
        else if (len==1){
            pushByte(nextByte);
            messageType = (short)(((short)bytes[0])<<8|(short)(bytes[1] & 0x00ff));
            if(messageType==10 || messageType==6){return DRIQDISCdecode(nextByte);}
        }
        else{
            if(messageType==1 || messageType==2 || messageType==7|| messageType==8) {return GenericDecode(nextByte);}
            if(messageType==3){return DATAdecode(nextByte);}
            if(messageType==4){return ACKdecode(nextByte);}
            if(messageType==5){return ERRdecode(nextByte);}
            if(messageType==9){return BCASTdecode(nextByte);}
        }
        return null; //not a line yet
    }

    @Override
    public byte[] encode(byte[] message) {
        return message; //uses utf8 by default
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }

        bytes[len++] = nextByte;
    }

    private byte[] GenericDecode(byte nextByte){
        if(nextByte==0){
            byte[] ans = Arrays.copyOfRange(bytes, 0, len);
            len = 0; 
            return ans;  
        }
        pushByte(nextByte);
        return null; //not a line yet
    }

    private byte[] DATAdecode(byte nextByte){
        if(len==2) {
            pushByte(nextByte);
            return null;
        }
        else if (len==3) {
            pushByte(nextByte);
            sizeOfData = (short)(((short)bytes[2])<<8|(short)(bytes[3] & 0x00ff));
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

    private byte[] DRIQDISCdecode(byte nextByte){
        byte[] ans = Arrays.copyOfRange(bytes, 0, len);
        len = 0; 
        return ans;  
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
