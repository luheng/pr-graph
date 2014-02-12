package data;

public class OCRSequence extends AbstractSequence {
	
	public String letters;
	public OCRCorpus corpus;
	 
	public OCRSequence(OCRCorpus corpus, int seqID, int foldID, int[] nodes, int[] tags)
	{
		super(seqID, foldID, nodes, tags);
		this.corpus = corpus;
		letters = "";
		for(int tagID : tags)
			letters += (char)('a' + tagID);
	}
	
	public void print() { }	

}
