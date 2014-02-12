package graph;

public class Edge implements Comparable<Object> {
	public final int neighbor;
	public final double weight;
	
	public Edge(int neighbor, double weight) {
		this.neighbor = neighbor;
		this.weight = weight;
	}
	
	public int compareTo(Object arg0) {
		Edge e2 = (Edge) arg0;
		if(neighbor == e2.neighbor) return 0;
		return weight < e2.weight ? -1 : 1;
	}
}
