package graph;

import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;

public class EdgeList {
	PriorityQueue<Edge> queue;
	HashSet<Integer> set;
	int maxLength;
	
	public EdgeList(int maxLength) {
		queue = new PriorityQueue<Edge>();
		set = new HashSet<Integer>();
		this.maxLength = maxLength;
	}
	
	public void freeze() {
		for (Iterator<Edge> itr = queue.iterator(); itr.hasNext(); ) {
			set.add(itr.next().neighbor);
		}
	}
	
	public boolean contains(int neighbor) {
		return set != null && set.contains(neighbor);
	}
	
	public void add(Edge e) {
		if(queue.size() < maxLength) {
			queue.add(e);
		}
		else if(e.weight > queue.peek().weight) {
			queue.poll();
			queue.add(e);
		}
	}
	
	public void symAdd(Edge e) {
		if (!set.contains(e.neighbor)) {
			queue.add(e);
			set.add(e.neighbor);
		}
	}
	
	public Iterator<Edge> iterator()
	{
		return queue.iterator();
	}
	
	public boolean isEmpty()
	{
		return queue.isEmpty();
	}
	
	public Edge poll()
	{
		Edge e = queue.poll();
		set.remove(e);
		return e;
	}
	
	public Edge peek()
	{
		return queue.peek();
	}

	public int size() {
		return queue.size();
	}
}