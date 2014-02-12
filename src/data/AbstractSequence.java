package data;

public class AbstractSequence {

	public int seqID, foldID, length;
	public int[] tags, tokens, nodes; 
	public boolean isLabeled;
	
	public AbstractSequence(int seqID, int foldID, int[] tokens, int[] tags)
	{
		this.seqID = seqID;
		this.foldID = foldID;
		this.tags = tags;
		this.tokens = tokens;
		this.length = tags.length;
		
		nodes = new int[length];
		for(int i = 0; i < length; i++) {
			nodes[i] = tokens[i];
		}
		
		this.isLabeled = false;
	}

	public void print() { 
		// TODO
	}	
}
