package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;
import socs.network.message.SOSPFPacket;
import socs.network.util.Configuration;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Vector;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  ServerSocket server;

  // Need a signal to read input (Y/N) from the main thread
  volatile int signal = -1;

  // A flag to denote whether the router has started or not
  boolean started = false;

  public Router(Configuration config) {
    // Initialize the router config
    rd.simulatedIPAddress = config.getString("socs.network.router.ip");
    // We need to setup the processIP and processPortNumber as well
    rd.processPortNumber = config.getShort("socs.network.router.port");
    try{
      rd.processIPAddress = java.net.InetAddress.getLocalHost().getHostAddress();
    }
    catch (Exception e){
      throw new RuntimeException(e);
    }

    try{
      server = new ServerSocket(rd.processPortNumber);
    }
    catch (Exception e){
      throw new RuntimeException(e);
    }

    // Need another thread to handle the request
    new Thread(new Runnable() {
      public void run() {
          requestHandler();
      }
    }).start();

    // Initialize the LSD
    lsd = new LinkStateDatabase(rd);

  }

  /**
   * output the shortest path to the given destination ip
   * <p/>
   * format: source ip address  -> ip address -> ... -> destination ip
   *
   * @param destinationIP the ip adderss of the destination simulated router
   */
  private void processDetect(String destinationIP) {
    String path = this.lsd.getShortestPath(destinationIP);
    System.out.println(path);
  }

  private boolean requestLinkDeletion(String processIP, short processPort, String simulatedIP){
    try{
      // Setup the socket used for communication and corresponding input and output
      Socket clientSocket = new Socket(processIP, processPort);
      ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
      ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream());

      // Construct a deletion packet
      SOSPFPacket deletion = new SOSPFPacket();
      deletion.srcProcessIP = this.rd.processIPAddress;
      deletion.srcProcessPort = this.rd.processPortNumber;
      deletion.srcIP = this.rd.simulatedIPAddress;
      deletion.dstIP = simulatedIP;
      deletion.sospfType = 3; // That is a deletion PACKET
      deletion.routerID = deletion.srcIP; // According to the explanation of TA on myCourses
      deletion.neighborID = deletion.dstIP;

      // Send the deletion request to the remote router
      outputStream.writeObject(deletion);

      try{
        SOSPFPacket receivedPacket = (SOSPFPacket) inputStream.readObject();
        if (receivedPacket.sospfType == 3) { //i.e., received a deletion reply
          // Close all the socket and streams
          clientSocket.close();
          inputStream.close();
          outputStream.close();
          return true;
        }
        else{
          // Close all the socket and streams
          clientSocket.close();
          inputStream.close();
          outputStream.close();
          return false;
        }
      }
      catch (Exception e){
        throw new RuntimeException(e);
      }
    }
    catch (Exception e){
      throw new RuntimeException(e);
    }
  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {
    //check to make sure the port number is valid
    // that it is not null
    // and that there actually exists a two-way link
    if (portNumber > 3 || portNumber < 0 || ports[portNumber] == null
            || ports[portNumber].router2.status != RouterStatus.TWO_WAY) {
      System.err.println("Invalid port error.");
      return;
    }

    RouterDescription rd = ports[portNumber].router2;

    //get in touch with this router so you can send link deletion request
    if (requestLinkDeletion(rd.processIPAddress, rd.processPortNumber, rd.simulatedIPAddress)) {
      this.ports[portNumber] = null;

      try {
        // Do the synchronization
        synchronize();
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      System.err.println("Error while trying to delete link.");
    }

  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * NOTE: this command should not trigger link database synchronization
   */
  private void processAttach(String processIP, short processPort,
                             String simulatedIP, short weight) {
    // Create the router description for the router to be attached
    RouterDescription toBeAttached = new RouterDescription();
    toBeAttached.processIPAddress = processIP;
    toBeAttached.processPortNumber = processPort;
    toBeAttached.simulatedIPAddress = simulatedIP;

    // Check if the router want to attach to itself
    if (toBeAttached.simulatedIPAddress.equals(this.rd.simulatedIPAddress)){
      System.err.println("YOU CANNOT ATTACH TO YOURSELF!");
      return;
    }

    // Check if the router has already attached to the desired router
    for(Link l : this.ports){
      // Check if l is null first to avoid the null pointer exception
      if(l != null && l.router2.simulatedIPAddress.equals(toBeAttached.simulatedIPAddress)){
        System.err.println("YOU HAVE ALREADY ATTACHED TO THIS ROUTER");
        return;
      }
    }

    // Find the first available port
    int available_port = -1;
    for(int i = 0; i < 4; i ++){
      if (this.ports[i] == null){
        available_port = i;
        break;
      }
    }
    // print out error if there's no more ports available
    if (available_port == -1){
      System.err.println("THERE IS NO PORT AVAILABLE RIGHT NOW!");
    }

    // Set up the desired attachment
    try {
      // Setup the socket used for communication and corresponding input and output
      Socket clientSocket = new Socket(processIP, processPort);
      ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
      ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream());

      // Forward some information for the router to be attached
      outputStream.writeObject(this.rd.simulatedIPAddress);
      // Also forward the port number since the server will add corresponding link to its ports array
      outputStream.writeObject(this.rd.processPortNumber);
      // Also forward the weight of the link
      outputStream.writeObject(weight);
      String option = (String) inputStream.readObject();
      // If the desired router accept the request, add the link to ports array
      if (option.equals("1")){
        System.out.println("Your attach request has been accepted;");
        this.ports[available_port] = new Link(this.rd, toBeAttached, weight);
      }
      // Otherwise, do nothing
      else{
        System.err.println("Your attach request has been rejected;");
      }
      // Close all the socket and streams
      clientSocket.close();
      inputStream.close();
      outputStream.close();
    }
    catch (Exception e){
      throw new RuntimeException(e);
    }
  }


  /**
   * process request from the remote router. 
   * For example: when router2 tries to attach router1. Router1 can decide whether it will accept this request. 
   * The intuition is that if router2 is an unknown/anomaly router, it is always safe to reject the attached request from router2.
   */
  private void requestHandler() {
    try{
      // We are supposed to handle the concurrent requests, so need multi threading here
      while (true){
        Socket socket = server.accept();
        Thread handler = new Thread(new RequestHandler(socket, this));
        handler.start();
      }
    }
    catch (Exception e){
      throw new RuntimeException(e);
    }
  }

  /**
   * broadcast Hello to neighbors
   */
  private void processStart() {
    // Check if the ports array is empty
    boolean empty = true;
    for (Link l : ports){
      empty = empty && (l == null);
    }
    if (empty){
      System.err.println("You have started but not connected to any other routers");
    }

    // change the flag to denote the router has started
    started = true;

    // Send Hello to every routers in the ports array
    for (Link l : ports){
      // Check if the link is null
      if (l == null){
        continue;
      }

      // Broadcast HELLO to all routers in ports array
      try{
        // Build socket connection
        String processIP = l.router2.processIPAddress;
        short processPort = l.router2.processPortNumber;
        Socket clientSocket = new Socket(processIP, processPort);
        ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
        ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream());

        // Set up the HELLO packet
        SOSPFPacket hello = new SOSPFPacket();
        hello.srcProcessIP = this.rd.processIPAddress;
        hello.srcProcessPort = this.rd.processPortNumber;
        hello.srcIP = this.rd.simulatedIPAddress;
        hello.dstIP = l.router2.simulatedIPAddress;
        hello.sospfType = 0; // That is a HELLO PACKET
        hello.routerID = hello.srcIP; // According to the explanation of TA on myCourses
        hello.neighborID = hello.dstIP;

        // send HELLO packet
        outputStream.writeObject(hello);

        // wait for the reply HELLO packet
        Object reply = inputStream.readObject();
        if (reply instanceof SOSPFPacket){
          SOSPFPacket helloBack = (SOSPFPacket) reply;
          // Check if the received packet is a HELLO packet
          if (helloBack.sospfType != 0){
            System.err.println("DID NOT RECEIVE HELLO BACK");
          }
          else{
            // Display the log and change the corresponding status
            System.out.println("received HELLO from " + helloBack.srcIP + ";");
            l.router1.status = RouterStatus.TWO_WAY;
            l.router2.status = RouterStatus.TWO_WAY;
            System.out.println("set " + helloBack.srcIP + " state to TWO_WAY;");
            // Send HELLO back again to inform the other router
            outputStream.writeObject(hello);

            // Do the synchronization
            synchronize();
          }
        }
        else{
          System.err.println("RECEIVED WRONG FORMAT OF REPLY!");
        }

        // Close all the socket and streams
        clientSocket.close();
        inputStream.close();
        outputStream.close();
      }
      catch (Exception e){
        throw new RuntimeException(e);
      }
    }

  }

  // This is the helper function for synchronization process, which will be used by START, CONNECT, DISCONNECT, QUIT
  public void synchronize() throws IOException {
    // Construct a LSA for current router first
    LSA lsa = new LSA();
    lsa.linkStateID = this.rd.simulatedIPAddress;
    // Check if this is the first LSA
    if (lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber == Integer.MIN_VALUE){
      lsa.lsaSeqNumber = 0;
    }
    else{
      int last = lsd._store.get(this.rd.simulatedIPAddress).lsaSeqNumber;
      lsa.lsaSeqNumber = last + 1;
    }
    // create link descriptions for each link
    LinkedList<LinkDescription> links = new LinkedList<LinkDescription>();
    for (Link p : ports){
      if (p != null && p.router2.status != null){
        LinkDescription description = new LinkDescription();
        description.linkID = p.router2.simulatedIPAddress;
        description.portNum = p.router2.processPortNumber;
        description.tosMetrics = p.weight;
        links.add(description);
      }
    }
    lsa.links = links;

    // Update the LinkStateDatabase
    lsd._store.put(lsa.linkStateID, lsa);

    // Send LSAUPATE to all connected routers
    for (Link l : ports){
      if (l == null){
        continue;
      }
      String processIP = l.router2.processIPAddress;
      short processPort = l.router2.processPortNumber;
      Socket clientSocket = new Socket(processIP, processPort);
      ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());

      // Create a LSAUPATE packet
      SOSPFPacket update = new SOSPFPacket();
      update.srcProcessIP = this.rd.processIPAddress;
      update.srcProcessPort = this.rd.processPortNumber;
      update.srcIP = this.rd.simulatedIPAddress;
      update.dstIP = l.router2.simulatedIPAddress;
      update.sospfType = 1; // That is a LSAUPDATE PACKET
      update.routerID = update.srcIP; // According to the explanation of TA on myCourses
      update.neighborID = update.dstIP;
      Vector<LSA> v = new Vector<LSA>();
      v.add(lsa);
      update.lsaArray = v;

      // Broadcast the packet
      outputStream.writeObject(update);

      // Close all the socket and streams
//      clientSocket.close();
//      outputStream.close();
    }
  }

  public void synchronize(SOSPFPacket packet) throws IOException {
    //forward packet to all non-null ports
    for (Link l : ports) {
      if (l != null) {

        Socket clientSocket = new Socket(l.router2.processIPAddress, l.router2.processPortNumber);

        ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());

        //broadcast the LSAUPDATE packet
        outputStream.writeObject(packet);

        // Close all the socket and streams
//        clientSocket.close();
//        outputStream.close();
      }
    }
  }

  public int getPort(String dest) {
    for (int i = 0; i < 4; i++) {
      if (this.ports[i] != null && this.ports[i].router2.simulatedIPAddress.equals(dest)) {
        return i;
      }
    }
    System.err.println("Specified IP does not exist.");
    return -1;
  }

  /**
   * attach the link to the remote router, which is identified by the given simulated ip;
   * to establish the connection via socket, you need to indentify the process IP and process Port;
   * additionally, weight is the cost to transmitting data through the link
   * <p/>
   * This command does trigger the link database synchronization
   */
  private void processConnect(String processIP, short processPort,
                              String simulatedIP, short weight) {
    if (this.started) {

      //check to make sure you aren't connecting to yourself
      if (simulatedIP.equals(this.rd.simulatedIPAddress)) {
        System.err.println("You can't connect to yourself!");
        return;
      }

      //check to make sure isn't already connected to requested remote router
      for (int x = 0; x < 4; x++) {
        if (this.ports[x] != null && this.ports[x].router2.simulatedIPAddress.equals(simulatedIP)) {
          System.err.println("You are already connected to this router!");
          return;
        }
      }

      // setup RouterDescription for the desired router
      RouterDescription remote = new RouterDescription();
      remote.processIPAddress = processIP;
      remote.processPortNumber = processPort;
      remote.simulatedIPAddress = simulatedIP;

      // find first available port
      // Find the first available port
      int available_port = -1;
      for(int i = 0; i < 4; i ++){
        if (this.ports[i] == null){
          available_port = i;
          break;
        }
      }

      // print out error if there's no more ports available
      if (available_port == -1){
        System.err.println("THERE IS NO PORT AVAILABLE RIGHT NOW!");
        return;
      }

      // attempt to connect with desired router
      try {
        Socket clientSocket = new Socket(processIP, processPort);
        ObjectOutputStream outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
        ObjectInputStream inputStream = new ObjectInputStream(clientSocket.getInputStream());

        // make sure the router is "connectable"
        // Forward some information for the router to be attached
        outputStream.writeObject(this.rd.simulatedIPAddress);
        // Also forward the port number since the server will add corresponding link to its ports array
        outputStream.writeObject(this.rd.processPortNumber);
        // Also forward the weight of the link
        outputStream.writeObject(weight);

        try {
          String incoming = (String) inputStream.readObject();

          if (incoming.equals("1")) {
            System.out.println("Your connect request has been accepted;");
            // if all goes well, assign the new router link to the available port
            this.ports[available_port] = new Link(rd, remote, weight);
            // Close all the socket and streams
            clientSocket.close();
            inputStream.close();
            outputStream.close();
          } else {
            System.err.println("Your connect request has been rejected;");
            System.err.println(incoming);
          }
        }
        catch (Exception e){
          throw new RuntimeException(e);
        }

      }
      catch (Exception e){
        throw new RuntimeException(e);
      }

      //Basically do start except with the messaging back and forth
      Socket clientSocket = null;
      ObjectOutputStream outputStream = null;
      ObjectInputStream inputStream = null;


      // Initialization section:
      // Try to open a socket on the given port
      // Try to open input and output streams
      String hostname = this.ports[available_port].router2.processIPAddress;
      short port = this.ports[available_port].router2.processPortNumber;

      try {
        clientSocket = new Socket(hostname, port);
        outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
        inputStream = new ObjectInputStream(clientSocket.getInputStream());
      }
      catch (Exception e){
        throw new RuntimeException(e);
      }

      // If everything has been initialized then we want to send a packet
      // to the socket we have opened a connection to on the given port
      try {
        SOSPFPacket packet = new SOSPFPacket();
        packet.srcProcessIP = this.rd.processIPAddress;
        packet.srcProcessPort = this.rd.processPortNumber;
        packet.srcIP = this.rd.simulatedIPAddress;
        packet.dstIP = this.ports[available_port].router2.simulatedIPAddress;
        packet.sospfType = 2; // That is a CONNECT packet
        packet.routerID = packet.srcIP; // According to the explanation of TA on myCourses
        packet.neighborID = packet.dstIP;
        packet.HelloWeight = weight;

        //broadcast the HELLO packet
        try {
          outputStream.writeObject(packet);
        } catch (NullPointerException e) {
          // Close all the socket and streams
          clientSocket.close();
          inputStream.close();
          outputStream.close();
          System.err.println("Trying to send a null packet!");
          return;
        }

        //wait for response
        Object incoming_unk;

        try {
          incoming_unk = inputStream.readObject();
        }
        catch (Exception e){
          // Close all the socket and streams
          clientSocket.close();
          inputStream.close();
          outputStream.close();
          throw new RuntimeException(e);
        }

        SOSPFPacket incoming;

        // this should only happen if receiving "Ports are full error". Use case ends
        if (incoming_unk instanceof String) {
          String temp = (String) incoming_unk;

          //need to delete current link from ports since it is impossible to have future conversation with this router
          this.ports[available_port] = null;

          System.out.println(temp + " Deleting link reference from port. Maybe try to connect again later.");
          // Close all the socket and streams
          clientSocket.close();
          inputStream.close();
          outputStream.close();
          return;

          //otherwise the HELLO packet was processed successfully
        } else {
          incoming = (SOSPFPacket) incoming_unk;
        }

        //check to make sure the packet received was a HELLO
        if (incoming == null || incoming.sospfType != 2) {
          System.out.println("Error: did not receive a CONNECT back!");

          // clean up
          // Close all the socket and streams
          clientSocket.close();
          inputStream.close();
          outputStream.close();
          return;
        }

        this.ports[available_port].router1.status = RouterStatus.TWO_WAY;
        this.ports[available_port].router2.status = RouterStatus.TWO_WAY;

        //send back the HELLO packet
        outputStream.writeObject(packet);

        //broadcast LSAUPDATE to neighbors
        synchronize();

        // Close all the socket and streams
        clientSocket.close();
        inputStream.close();
        outputStream.close();

      }
      catch (Exception e){
        throw new RuntimeException(e);
      }

    } else {
      System.err.println("You must run start command before attempting to connect!");
    }
  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    // Check if the ports array is empty
    boolean empty = true;
    for (Link l : ports){
      empty = empty && (l == null);
    }
    if (empty){
      System.err.println("There is no neighbors;");
    }
    else{
      // check if there is attached router but the links have not been set up
      boolean ifAttached = true;
      int neighbor = 1;
      for (Link l : ports){
        if (l != null && l.router2.status == RouterStatus.TWO_WAY){
          ifAttached = false;
          System.out.println("IP address of neighbor" + neighbor + ": " + l.router2.simulatedIPAddress);
          neighbor ++;
        }
      }
      if (ifAttached){
        System.err.println("You have attached to other routers but not started yet;");
      }
    }


  }

  private boolean isConnected(){
    for (Link port : this.ports) {
      if (port != null && port.router1.status == RouterStatus.TWO_WAY) {
        return true;
      }
    }
    return false;
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {
    //check to make sure you are connected to any remote routers
    if (!isConnected()) {
      System.out.println("Quitting process was successful.");
      System.exit(0);
    }

    //if you are connected to remote routers, then you need to disconnect from each one before exiting
    for (int i = 0; i < 4; i++) {

      //you can skip any null or non-started ports
      if (ports[i] == null || ports[i].router2.status != RouterStatus.TWO_WAY) continue;

      processDisconnect((short) i);

    }

    System.out.println("Quitting process was successful.");

    System.exit(0);
  }

  /**
   * update the weight of an attached link
   */
  private void updateWeight(String processIP, short processPort,
                             String simulatedIP, short weight){

  }

  private void processPortInfo(){
    int portNum = 0;
    for (Link l : this.ports){
      if (l == null) {
        System.out.println("The Router Connected in Port " + portNum + " is: None");
      }
      else{
        System.out.println("The Router Connected in Port " + portNum + " is: " + l.router2.simulatedIPAddress);
      }
      portNum ++;
    }
  }

  public void terminal() {
    try {
      InputStreamReader isReader = new InputStreamReader(System.in);
      BufferedReader br = new BufferedReader(isReader);
      System.out.print(">> ");
      String command = br.readLine();
      while (true) {
        if (command.startsWith("detect ")) {
          String[] cmdLine = command.split(" ");
          processDetect(cmdLine[1]);
          System.out.print(">> ");
        } else if (command.startsWith("disconnect ")) {
          String[] cmdLine = command.split(" ");
          processDisconnect(Short.parseShort(cmdLine[1]));
          System.out.print(">> ");
        } else if (command.startsWith("quit")) {
          processQuit();
          System.out.print(">> ");
        } else if (command.startsWith("attach ")) {
          String[] cmdLine = command.split(" ");
          processAttach(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
          System.out.print(">> ");
        } else if (command.equals("start")) {
          processStart();
          System.out.print(">> ");
        } else if (command.startsWith("connect ")) {
          String[] cmdLine = command.split(" ");
          processConnect(cmdLine[1], Short.parseShort(cmdLine[2]),
                  cmdLine[3], Short.parseShort(cmdLine[4]));
          System.out.print(">> ");
        } else if (command.equals("neighbors")) {
          //output neighbors
          processNeighbors();
          System.out.print(">> ");
        } else if (command.equals("Y")) {
          signal = 1;
        } else if (command.equals("N")) {
          signal = 0;
        } else if (command.equals("portInfo")) {
          //output port information
          processPortInfo();
          System.out.print(">> ");
        } else {
          //invalid command
          break;
        }
        command = br.readLine();
      }
      isReader.close();
      br.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
