package models;

import data.AbstractCorpus;

public class UnconstrainedFactorIterator implements AbstractFactorIterator {
	int[] sentenceBoundaries;
	final int[] S0, S00, SN, S; // initial states and final states
	
	public UnconstrainedFactorIterator(AbstractCorpus corpus) {
		sentenceBoundaries = new int[corpus.numInstances];
		for (int i = 0; i < corpus.numInstances; i++) {
			sentenceBoundaries[i] = corpus.getInstance(i).length;
		}
		S0 = new int[1]; 
		S0[0] = corpus.initialState;
		S00 = new int[1];
		S00[0] = corpus.initialStateSO;
		SN = new int[1];
		SN[0] = corpus.finalState;
		S = new int[corpus.numTags];
		for (int i = 0; i < corpus.numTags; i++) {
			S[i] = i;
		}
	}
	
	@Override
	public int[] states(int sentenceID, int position) {
		if (position < 0) {
			return position < -1 ? S00 : S0;
		}
		else if (position < sentenceBoundaries[sentenceID]) { 
			return S;
		}
		else { 
			return SN;
		}
	}

}
