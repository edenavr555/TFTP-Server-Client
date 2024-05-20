package bgu.spl.net.impl.tftp;
import java.io.IOException;
import java.util.HashMap;

import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

public class TftpConnections<T> implements Connections<T> {

    HashMap<Integer, ConnectionHandler<T>> connections;

    public TftpConnections(){
        this.connections = new HashMap<>();
    }

    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler){
        this.connections.put(connectionId, handler);
    }

    public boolean send(int connectionId, T msg){
        if(this.connections.containsKey(connectionId)){
            this.connections.get(connectionId).send(msg);
            return true;
        }
        return false;
    }

    public void disconnect(int connectionId){
        this.connections.remove(connectionId);
    }

    public void closeAllConnections(){
        for(int id: connections.keySet()){
            try{
            connections.get(id).close();
            }
            catch(IOException e){}
        }
    }


}
