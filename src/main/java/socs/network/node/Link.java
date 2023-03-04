package socs.network.node;

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
