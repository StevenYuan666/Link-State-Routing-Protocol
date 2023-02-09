package socs.network.node;

import java.io.*;
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
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
            Object request = inputStream.readObject();

            // Check if we are not receiving a null reference
            if (request != null){
                // Handle the attach request (only receive string for attach request)
                if (request instanceof String){
                    String simulatedIP = (String) request;
                    Short port = (Short) inputStream.readObject();
                    short portNumber = port.shortValue();
                    handleAttach(outputStream, simulatedIP, portNumber);
                }
            }
            else{
                System.err.println("Received a null packet!");
            }

            // Close all the socket and streams
            socket.close();
            inputStream.close();
            outputStream.close();


        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void handleAttach(ObjectOutputStream outputStream, String simulatedIP, short portNumber) throws IOException {

        System.out.println("\nreceived HELLO from " + simulatedIP + ";");
        System.out.println("Do you accept this request? (Y/N)");

        // Wait for the input from user
        while (router.signal == -1){
            continue;
        }

        // Find the first available port
        int available_port = -1;
        for(int i = 0; i < 4; i ++){
            if (router.ports[i] == null){
                available_port = i;
                break;
            }
        }
        // reject the attach request if there's no more ports available
        if (available_port == -1){
            outputStream.writeObject("0");
            System.out.println("The attach request is automatically rejected since no more port is available;");
            System.out.print(">> ");
        }

        if (router.signal == 1){
            outputStream.writeObject("1");
            System.out.println("You accepted the attach request;");
            System.out.print(">> ");
            // Add a link to the receiver router
            RouterDescription requestRd = new RouterDescription();
            requestRd.processIPAddress = socket.getLocalAddress().getHostAddress();
            requestRd.processPortNumber = portNumber;
            requestRd.simulatedIPAddress = simulatedIP;
            router.ports[available_port] = new Link(router.rd, requestRd);
        }
        else if (router.signal == 0){
            outputStream.writeObject("0");
            System.out.println("You rejected the attach request;");
            System.out.print(">> ");
        }

        // Reset the signal
        router.signal = -1;
    }
}
