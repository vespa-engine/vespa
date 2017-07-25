package com.yahoo.vespa.hosted.node.verification.hardware.net;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * Created by olaa on 19/07/2017.
 * Sends file to server, for checking connection speed.
 * Not used, can be deleted
 */
public class Client {

    public void sendFile(Socket socket, File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file);
             DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {
            int fileSize = (int) file.length();
            byte[] buffer = new byte[fileSize];
            outputStream.writeUTF(file.getName());
            int receivedBytesCount;
            while ((receivedBytesCount = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, receivedBytesCount);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            socket.close();
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        File file = new File("src/test/resources/testReadFile.txt");
        double start = System.currentTimeMillis() / 1000.0;
        try {
            client.sendFile(new Socket("localhost", 10000), file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        double finish = System.currentTimeMillis() / 1000.0;
        System.out.println(((double) file.length() / (finish - start)) + " B/s");
    }

}
