package com.yahoo.vespa.hosted.node.verification.hardware.net;

import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by olaa on 19/07/2017.
 */
public class Server {

    public void serve(int portNumber) throws IOException{
        ServerSocket server;
        Socket client;
        server = new ServerSocket(portNumber);
        client = server.accept();
        DataInputStream dataInputStream = new DataInputStream(client.getInputStream());
        DataInputStream inputStream = new DataInputStream(client.getInputStream());
        String fileName = dataInputStream.readUTF();
        FileOutputStream fileOutputStream = new FileOutputStream("./" + fileName);
        byte[] buffer = new byte[65535];
        int currentLength;
        while((currentLength = inputStream.read(buffer)) != -1){
            fileOutputStream.write(buffer, 0, currentLength);
        }

    }

    public static void main(String[] args) {
        Server server = new Server();
        try {
            server.serve(10000);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}
