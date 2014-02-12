package data;

public class PosSequence extends AbstractSequence {

	public PosCorpus corpus;
		
	public PosSequence(PosCorpus corpus, int seqID, int foldID, int[] words, int[] tags) 
	{
		super(seqID, foldID, words, tags);
		this.corpus = corpus;
	}
	
	public void print()
	{
		System.out.println(seqID + "\t(" + foldID + ")");
		for(int i = 0; i < length; i++) {
			System.out.print(corpus.index2word.get(tokens[i]) + "[" 
					+ corpus.index2tag.get(tags[i]) + "]\t");
		}
		System.out.println();
	}

	public String getWord(int pos) {
		if(pos == -1) return "<s0>"; 
		else return pos == length ? "</s0>" : 
			corpus.index2word.get(tokens[pos]);
	}
	
	public String getTag(int pos) {
		if(pos == -1) return "<t0>"; 
		else return pos == length ? "</t0>" : 
			corpus.index2tag.get(tags[pos]);
	}

}
