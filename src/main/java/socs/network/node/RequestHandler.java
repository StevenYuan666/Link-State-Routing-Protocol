package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

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
                    Short w = (Short) inputStream.readObject();
                    short weight = w.shortValue();
                    handleAttach(outputStream, simulatedIP, portNumber, weight);
                }
                // Otherwise, we receive a packet
                else{
                    SOSPFPacket packet = (SOSPFPacket) request;
                    // If this is a HELLO packet
                    if (packet.sospfType == 0){
                        try {
                            handleHello(packet, outputStream, inputStream);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    // If this is a LSAUPDATE packet
                    else if (packet.sospfType == 1){
                        handleLSAUPDATE(packet, outputStream, inputStream);
                    }
                    // If this is a connect packet
                    else if (packet.sospfType == 2){
                        handleConnect(packet, outputStream, inputStream);
                    }
                    // If this is a disconnect packet
                    else if (packet.sospfType == 3){
                        handleDisconnect(packet, outputStream, inputStream);
                    }
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

    private void handleAttach(ObjectOutputStream outputStream, String simulatedIP, short portNumber, short weight) throws IOException {

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
            System.out.println("The request is automatically rejected since no more port is available;");
            System.out.print(">> ");
        }

        if (router.signal == 1){
            outputStream.writeObject("1");
            System.out.println("You accepted the request;");
            System.out.print(">> ");
            // Add a link to the receiver router
            RouterDescription requestRd = new RouterDescription();
            requestRd.processIPAddress = socket.getLocalAddress().getHostAddress();
            requestRd.processPortNumber = portNumber;
            requestRd.simulatedIPAddress = simulatedIP;
            router.ports[available_port] = new Link(router.rd, requestRd, weight);
        }
        else if (router.signal == 0){
            outputStream.writeObject("0");
            System.out.println("You rejected the request;");
            System.out.print(">> ");
        }

        // Reset the signal
        router.signal = -1;
    }

    private void handleHello(SOSPFPacket packet, ObjectOutputStream outputStream, ObjectInputStream inputStream)
            throws IOException, ClassNotFoundException, InterruptedException {
        System.out.println("\nreceived HELLO from " + packet.srcIP + ";");
        // Change the status and display the log
        int port = -1;
        for (int i = 0; i < 4; i ++){
            if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(packet.srcIP)){
                port = i;
                break;
            }
        }
        router.ports[port].router1.status = RouterStatus.INIT;
        router.ports[port].router2.status = RouterStatus.INIT;
        System.out.println("set " + packet.srcIP + " state to INIT;");

        // Construct the hello back packet
        SOSPFPacket hello = new SOSPFPacket();
        hello.srcProcessIP = router.rd.processIPAddress;
        hello.srcProcessPort = router.rd.processPortNumber;
        hello.srcIP = router.rd.simulatedIPAddress;
        hello.dstIP = router.ports[port].router2.simulatedIPAddress;
        hello.sospfType = 0; // That is a HELLO PACKET
        hello.routerID = hello.srcIP; // According to the explanation of TA on myCourses
        hello.neighborID = hello.dstIP;

        // Send back
        outputStream.writeObject(hello);

        // Wait for the helloBack
        SOSPFPacket newPacket = (SOSPFPacket) inputStream.readObject();
        // Check if the reply is a HELLO packet
        if (newPacket == null || newPacket.sospfType != 0){
            System.err.println("DID NOT RECEIVE HELLO BACK!");
            return;
        }
        // Change the status and display the log
        System.out.println("received HELLO from " + newPacket.srcIP + ";");
        router.ports[port].router1.status = RouterStatus.TWO_WAY;
        router.ports[port].router2.status = RouterStatus.TWO_WAY;
        System.out.println("set " + newPacket.srcIP + " state to TWO_WAY;");

        // Do synchronization
        router.synchronize();

        TimeUnit.SECONDS.sleep(2);
        // For beautiful format
        System.out.print(">> ");
    }

    private void handleLSAUPDATE(SOSPFPacket packet, ObjectOutputStream outputStream,
                                 ObjectInputStream inputStream) throws IOException {
        LSA lsa = router.lsd._store.get(packet.srcIP);

        // Need to a flag to denote whether this is the first LSA
        boolean ifNew = false;

        if (lsa == null || (packet.lsaArray.lastElement().lsaSeqNumber > lsa.lsaSeqNumber)){
            ifNew = (lsa == null);

            // check if the sender router is direct neighbor of the current router
            boolean ifDirect = false;
            int port = -1;

            //compare IP address to the source's IP address
            for (int i = 0; i < 4; i++) {
                if (router.ports[i] != null &&
                        router.ports[i].router2.simulatedIPAddress.equals(packet.srcIP)) {
                    ifDirect = true;
                    port = i;
                    break;
                }
            }

            // If it is a direct neighbor, then update links in the port and LSA
            if (ifDirect){
                // Find the link description for the current router
                LinkedList<LinkDescription> listOfDescription = packet.lsaArray.lastElement().links;
                LinkDescription ld = null;
                for (LinkDescription d : listOfDescription){
                    if (d.linkID.equals(router.rd.simulatedIPAddress)){
                        ld = d;
                        break;
                    }
                }

                if (ld != null) {
                    //if the LSA is saying that there is an outdated weight, then update weight in port and LinkState
                    if (ld.tosMetrics != router.ports[port].weight && ld.tosMetrics > -1) {

                        // update link description in port
                        router.ports[port].weight = (short) ld.tosMetrics;

                        // get current LSA from LSD
                        LSA current = router.lsd._store.get(router.rd.simulatedIPAddress);

                        //get new link descriptions
                        // create link descriptions for each link
                        LinkedList<LinkDescription> links = new LinkedList<LinkDescription>();
                        for (Link p : router.ports){
                            if (p != null && p.router2.status != null){
                                LinkDescription description = new LinkDescription();
                                description.linkID = p.router2.simulatedIPAddress;
                                description.portNum = p.router2.processPortNumber;
                                description.tosMetrics = p.weight;
                                links.add(description);
                            }
                        }
                        current.links = links;
                        router.lsd._store.put(router.rd.simulatedIPAddress, current);
                        router.synchronize();
                    }
                }
            }

            router.lsd._store.put(packet.srcIP, packet.lsaArray.lastElement());

            // Broadcast LSAUPDATE to all neighbors
            for (Link l : router.ports){
                if (l != null && !l.router2.simulatedIPAddress.equals(packet.srcIP)) {
                    router.synchronize(packet);
                    if (ifNew){
                        // If this is a new router, you need to broadcast yourself.
                        router.synchronize();
                    }
                }
            }
        }
    }

    private void handleConnect(SOSPFPacket packet, ObjectOutputStream outputStream, ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
        // check to make sure link doesnt already exist so that you dont add duplicates
        String tempIP = packet.srcIP;
        boolean exists = false;

        int port = -1;

        for (int i = 0; i < 4; i++) {
            if (router.ports[i] != null && router.ports[i].router2.simulatedIPAddress.equals(tempIP)) {
                exists = true;
                port = i;
                break;
            }
        }

        //if the current link does not exist...
        if (!exists) {

            //find next available port
            int available_port = -1;
            for(int i = 0; i < 4; i ++){
                if (router.ports[i] == null){
                    available_port = i;
                    break;
                }
            }

            //no available ports, return error
            if (available_port == -1) {
                outputStream.writeObject("Error: All ports on the requested router are busy!");
                // Close all the socket and streams
                socket.close();
                inputStream.close();
                outputStream.close();
                return;
            }

            //otherwise, create the new link
            RouterDescription router2 = new RouterDescription();
            router2.processIPAddress = packet.srcProcessIP;
            router2.processPortNumber = packet.srcProcessPort;
            router2.simulatedIPAddress = packet.srcIP;
            router.ports[available_port] = new Link(router.rd, router2, packet.HelloWeight);
            port = available_port;
        }


        //Change the status of the client router to INIT
        router.ports[port].router2.status = RouterStatus.INIT;
        router.ports[port].router1.status = RouterStatus.INIT;

        //Create outgoing packet
        SOSPFPacket newPacket = new SOSPFPacket();
        newPacket.srcProcessIP = router.rd.processIPAddress;
        newPacket.srcProcessPort = router.rd.processPortNumber;
        newPacket.srcIP = router.rd.simulatedIPAddress;
        newPacket.dstIP = router.ports[port].router2.simulatedIPAddress;
        newPacket.sospfType = 2; // That is a CONNECT PACKET back
        newPacket.routerID = newPacket.srcIP; // According to the explanation of TA on myCourses
        newPacket.neighborID = newPacket.dstIP;
        newPacket.HelloWeight = packet.HelloWeight;

        outputStream.writeObject(newPacket);

        //Wait for response
        packet = (SOSPFPacket) inputStream.readObject();

        //check to make sure the packet received was a HELLO
        if (packet == null || packet.sospfType != 2) {

            System.out.println("Error: did not receive a CONNECT back!");

            // Close all the socket and streams
            socket.close();
            inputStream.close();
            outputStream.close();
            return;
        }

        router.ports[port].router2.status = RouterStatus.TWO_WAY;
        router.ports[port].router1.status = RouterStatus.TWO_WAY;

        //broadcast LSAUPDATE to neighbors
        router.synchronize();

        // Close all the socket and streams
        socket.close();
        inputStream.close();
        outputStream.close();
    }

    private void handleDisconnect(SOSPFPacket packet, ObjectOutputStream outputStream, ObjectInputStream inputStream)
            throws IOException {
        //create the response packet
        SOSPFPacket response = new SOSPFPacket();
        response.srcProcessIP = router.rd.processIPAddress;
        response.srcProcessPort = router.rd.processPortNumber;
        response.srcIP = router.rd.simulatedIPAddress;
        response.dstIP = packet.srcIP;
        response.sospfType = 3; // That is a DISCONNECT PACKET
        response.routerID = response.srcIP; // According to the explanation of TA on myCourses
        response.neighborID = response.dstIP;

        //send the response to the source so it can update it's link state database
        outputStream.writeObject(response);

        //proceed to update link state database
        int port = router.getPort(packet.srcIP);

        router.ports[port] = null;

        router.synchronize();
    }
}
