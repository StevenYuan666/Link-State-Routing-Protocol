package socs.network.node;

import socs.network.util.Configuration;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;


public class Router {

  protected LinkStateDatabase lsd;

  RouterDescription rd = new RouterDescription();

  //assuming that all routers are with 4 ports
  Link[] ports = new Link[4];

  ServerSocket server;

  // Need a signal to read input (Y/N) from the main thread
  volatile int signal = -1;

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

  }

  /**
   * disconnect with the router identified by the given destination ip address
   * Notice: this command should trigger the synchronization of database
   *
   * @param portNumber the port number which the link attaches at
   */
  private void processDisconnect(short portNumber) {

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
      String option = (String) inputStream.readObject();
      // If the desired router accept the request, add the link to ports array
      if (option.equals("1")){
        System.out.println("Your attach request has been accepted;");
        this.ports[available_port] = new Link(this.rd, toBeAttached);
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

  }

  /**
   * output the neighbors of the routers
   */
  private void processNeighbors() {
    System.out.println("NEIGHBORS");
  }

  /**
   * disconnect with all neighbors and quit the program
   */
  private void processQuit() {

  }

  /**
   * update the weight of an attached link
   */
  private void updateWeight(String processIP, short processPort,
                             String simulatedIP, short weight){

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
        } else if (command.equals("connect ")) {
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
