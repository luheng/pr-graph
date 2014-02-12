package models;

import data.PosCorpus;
import data.RegexHelper;

public class PrunedTagIterator extends UnconstrainedFactorIterator {
	int numWords, numTags;
	int[][] allowedTags;
	int[][] wordIds;
	int[] SNUM, SPUNC;
	
	public PrunedTagIterator(PosCorpus corpus) {
		super(corpus);

		numWords = corpus.numWords;
		numTags = corpus.numTags; // universal pos-tags here
		allowedTags = new int[numWords][]; 
		SNUM = new int[1];
		SNUM[0] = corpus.lookupTag("NUM");
		SPUNC = new int[1];
		SPUNC[0] = corpus.lookupTag(".");
		
		for (int i = 0; i < numWords; i++) { 
			String token = corpus.getWord(i);
			if (RegexHelper.isPunctuation(token)) {
				allowedTags[i] = SPUNC;
			}
			else { 
				allowedTags[i] = S;
			}
		}
		
		wordIds = new int[corpus.numInstances][];
		for (int i = 0; i < corpus.numInstances; i++) {
			wordIds[i] = corpus.getInstance(i).tokens;
		}
	}
	
	@Override
	public int[] states(int sentenceID, int position) {
		if (position < 0) {
			return position < -1 ? S00 : S0;
		}
		else if (position < sentenceBoundaries[sentenceID]) { 
			return allowedTags[wordIds[sentenceID][position]];
		}
		else { 
			return SN;
		}
	}

}
