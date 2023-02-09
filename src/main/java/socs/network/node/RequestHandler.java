package socs.network.node;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;

public class RequestHandler extends Thread{
    private Socket socket;
    private Router router;
    public RequestHandler(Socket inSocket, Router inRouter){
        socket = inSocket;
        router = inRouter;
    }

    public void run(){
        try {
            DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream());
            DataInputStream inputStream = new DataInputStream(socket.getInputStream());
            String simulatedIP = inputStream.readUTF();
            System.out.println("\nreceived HELLO from " + simulatedIP + ";");
            System.out.println("Do you accept this request? (Y/N)");

            // Wait for the input from user
            while (router.signal == -1){
                continue;
            }

            if (router.signal == 1){
                outputStream.writeUTF("1");
                System.out.println("You accepted the attach request;");
                System.out.print(">> ");
            }
            else if (router.signal == 0){
                outputStream.writeUTF("0");
                System.out.println("You rejected the attach request;");
                System.out.print(">> ");
            }

            // Reset the signal
            router.signal = -1;

            // Close all the socket and streams
            socket.close();
            inputStream.close();
            outputStream.close();

            // Kill the child thread
            stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
