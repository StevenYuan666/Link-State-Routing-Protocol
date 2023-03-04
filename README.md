# Link-State-Routing-Protocol

## Programming Assignment1

### Overall Design

**To give a detailed description of our implementation in programming assignment 1, we would like to explain the high-level design first, then elaborate on our implementation choice for each command and give a toy example last.**

![overall_design](./overall_design.png)

In our implementation, each Router process has several threads to handle concurrent requests from other routers. The main thread(the command line terminal) is responsible for handling different functionalities, such as attach, start, and neighbors for the router. On top of the main thread, there is a concurrent child thread to listen to the requests from other routers, and each time it hears from another router, a child thread is created to handle that request, as shown in the picture above.

### Implementation Choice for Each Command

#### *attach*

**Accept/Reject**: In our implementation, the main thread will be used to read the input from the user (i.e., Y/N) and then pass the input to the child thread (namely the one responsible for listening) through a `volatile` variable. 

```java
else if (command.equals("Y")) {
  signal = 1;
} else if (command.equals("N")) {
  signal = 0;
} 
```

**Automatic rejection**: Even though we enable the routers to accept/decline an `attach` request based on the terminal input `(Y/N)`, an `attach` request will be automatically rejected if the receiver router has no port available.

```java
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
```

**Forward port number and weight**: In `processAttach`, we forward the `simulatedIP`, `processPortNumber`, and `weight` to the receiver router so that the receiver router can set up the link correspondingly.

```java
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
```

**Add weight to link**: In addition to the given template, we added an attribute in `Link` class to denote the weight for each link.

```java
public class Link {

  RouterDescription router1;
  RouterDescription router2;
  short weight;

  public Link(RouterDescription r1, RouterDescription r2, short inWeight) {
    router1 = r1;
    router2 = r2;
    weight = inWeight;
  }
}
```

#### *start*

There is no special implementation choice for `start`. (Simply follow the instruction in the assignment's description)

#### *neighbors*

There is no special implementation choice for `neighbors`.

### *A Toy Example*

The toy example in the following shows how three routers are connected to each other. Firstly, we start the process with each router, and then we use the command "attach" to attach router 2 to router 1.  After router 1 accepts the attached request from router 2, router 2 shows that the attach is successful, and then we use the start command to make them actually connected. After both states of router 1 and router 2 become TWO_WAY,  they appear in each other's neighbours, as shown in the picture. After testing the connection of 2 routers, we also add a third router to check multiple connections, so we make router 3 attach to router 1 using the "attach" and "start" commands. After that, we can see that now router 1 has two neighbours who are router 3 and router 2, while router 3 has neighbour 1 and router 2 has neighbour 1 as well.

![router1](./router1.png)

![router2](./router2.png)

![router3](./router3.png)



