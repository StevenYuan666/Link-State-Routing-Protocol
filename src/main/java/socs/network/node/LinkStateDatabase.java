package socs.network.node;

import socs.network.message.LSA;
import socs.network.message.LinkDescription;

import java.util.HashMap;
import java.util.HashSet;
import java.util.*;

public class LinkStateDatabase {

  //linkID => LSAInstance
  HashMap<String, LSA> _store = new HashMap<String, LSA>();

  private RouterDescription rd = null;

  public LinkStateDatabase(RouterDescription routerDescription) {
    rd = routerDescription;
    LSA l = initLinkStateDatabase();
    _store.put(l.linkStateID, l);
  }

  /**
   * output the shortest path from this router to the destination with the given IP address
   */
  public String getShortestPath(String destinationIP) {
    // create needed data structures
    HashSet<LSA> S = new HashSet<LSA>();
    HashSet<LSA> Q = new HashSet<LSA>();
    HashMap<LSA, Integer> distance = new HashMap<LSA, Integer>();
    HashMap<LSA, LSA> predecessors = new HashMap<LSA, LSA>();

    // init single source
    LSA source = this._store.get(rd.simulatedIPAddress);
    distance.put(source, 0);
    Q.add(source);

    // main loop of Djikstra
    while (Q.size() > 0) {

      // get the minimum of the priority queue
      LSA node = getMinimum(Q, distance);

      //add it to the confirmed list
      S.add(node);

      //remove from priority queue
      Q.remove(node);

      //expand its neighbors and find the closest to the current node
      // find minimal distance
      //grab all the neighbors of the current node
      LinkedList<LSA> adjacentNodes = getNeighbors(node);

      //look at each neighbor node
      for (LSA target : adjacentNodes) {

        if (target == null) continue;

        //if the current distance to the target (an arbitrary neighbor) is greater than the distance to the current node + the target
        if (getShortestDistance(target, distance) > getShortestDistance(node, distance) + getDistance(node, target)) {
          //add the neighbor to the distance, predecessors, and tentative list
          distance.put(target, getShortestDistance(node, distance) + getDistance(node, target));
          predecessors.put(target, node);
          Q.add(target);

        }
      }
    }

    LinkedList<LSA> path = getPath(this._store.get(destinationIP), predecessors);


    if (path == null || path.size() == 0) {
      return "Path does not exist";
    }

    return pathToString(path);
  }

  private int getDistance(LSA node, LSA target) {

    for (LinkDescription link : node.links) {

      if (link.linkID.equals(target.linkStateID)) {
        return link.tosMetrics;
      }
    }
    throw new RuntimeException("Should not happen");
  }

  private LinkedList<LSA> getNeighbors(LSA node) {
    LinkedList<LSA> neighbors = new LinkedList<LSA>();

    for (LinkDescription link : node.links) {
      LSA temp = _store.get(link.linkID);
      neighbors.add(temp);
    }
    return neighbors;
  }

  //finds the link with the minimal distance
  private LSA getMinimum(Set<LSA> tentative, HashMap<LSA, Integer> distance) {
    LSA minimum = null;

    //for all routers in the tenative list..
    for (LSA router : tentative) {

      //if minimum value has not been set yet then set first router to minimum
      minimum = (minimum == null || getShortestDistance(router, distance) <
              getShortestDistance(minimum, distance)) ? router : minimum;
    }
    return minimum;
  }

  private int getShortestDistance(LSA destination, HashMap<LSA, Integer> distance) {

    return (distance.get(destination) == null) ? Integer.MAX_VALUE : distance.get(destination);
  }

  private LinkedList<LSA> getPath(LSA target, HashMap<LSA, LSA> predecessors) {
    LinkedList<LSA> path = new LinkedList<LSA>();
    LSA step = target;

    // check if a path exists
    if (predecessors.get(step) == null) {
      return null;
    }

    path.add(step);

    while (predecessors.get(step) != null) {
      step = predecessors.get(step);
      path.add(step);
    }

    // Put it into the correct order
    Collections.reverse(path);

    return path;
  }

  private String pathToString(LinkedList<LSA> path) {
    //process the path to get the proper output
    String output = "";

    ListIterator<LSA> listIterator = path.listIterator();

    while (listIterator.hasNext()) {
      LSA current = listIterator.next();

      String ip = current.linkStateID;


      if (listIterator.hasNext()) {
        String arrow = " -> ";

        String neighbor = listIterator.next().linkStateID;
        listIterator.previous();

        int weight = -1;

        for (LinkDescription ld : current.links) {
          if (ld.linkID.equals(neighbor)) {
            weight = ld.tosMetrics;
          }
        }

        String w = " (" + weight + ") ";

        output = output + ip + " " + arrow + w;
      } else {
        output = output + " " + ip;
      }
    }

    return output;
  }

  //initialize the linkstate database by adding an entry about the router itself
  private LSA initLinkStateDatabase() {
    LSA lsa = new LSA();
    lsa.linkStateID = rd.simulatedIPAddress;
    lsa.lsaSeqNumber = Integer.MIN_VALUE;
    LinkDescription ld = new LinkDescription();
    ld.linkID = rd.simulatedIPAddress;
    ld.portNum = -1;
    ld.tosMetrics = 0;
    lsa.links.add(ld);
    return lsa;
  }


  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (LSA lsa: _store.values()) {
      sb.append(lsa.linkStateID).append("(" + lsa.lsaSeqNumber + ")").append(":\t");
      for (LinkDescription ld : lsa.links) {
        sb.append(ld.linkID).append(",").append(ld.portNum).append(",").
                append(ld.tosMetrics).append("\t");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

}
