package features;

import gnu.trove.TIntIntHashMap;
import java.util.Arrays;
import config.OCRConfig;
import data.OCRCorpus;

public class OCRFOPotentialFunction extends FirstOrderPotentialFunction {
	OCRCorpus myCorpus;
	OCRConfig config;
	
	public OCRFOPotentialFunction(OCRCorpus corpus, OCRConfig config)
	{
		super(corpus);
		this.myCorpus = corpus;
		this.config = config;
	
		processCorpus();
	}
	
	private void processCorpus() 
	{
		System.out.println(String.format("Extract features from %d nodes and %d/%d states.", 
				numWordTypes, numTStates, numStates));
		
		for(int i = 0; i < numTStates; i++) {
			extractTransitionFeatures(i, S0);
			extractTransitionFeatures(SN, i);
			for(int j = 0; j < numTStates; j++) 
				extractTransitionFeatures(i, j);
		}
		
		for(int i = 0; i < numWordTypes; i++) {
			for(int j = 0; j < numTStates; j++) {
				extractEmissionFeatures(0, i, j); // previous character
				extractEmissionFeatures(1, i, j); // current character
				extractEmissionFeatures(2, i, j); // next character
			}
			
			extractEmissionFeatures(2, i, S0);
			extractEmissionFeatures(0, i, SN); 
		}
		
		assignFeatureValues(config.scaleFeatures);
	}

	private void extractTransitionFeatures(int s, int sp) {
		String spChar = myCorpus.getTag(sp);
		String sChar = myCorpus.getTag(s);
		
		TIntIntHashMap fv = new TIntIntHashMap();
		add(fv, "S-1=" + spChar, true);
		add(fv, "S-0=" + sChar, true);
		add(fv, "S-10=" + spChar + sChar, true); 
		
		edgeFeatures[s][sp] = fv.keys();
		Arrays.sort(edgeFeatures[s][sp]);
		edgeFeatureVal[s][sp] = new double[fv.size()];
	}

	
	private void extractEmissionFeatures(int offset, int nid, int s) 
	{
		int[] pixels = myCorpus.getWord(nid);
		char sChar = myCorpus.getTag(s).charAt(0);
		
		TIntIntHashMap fv = new TIntIntHashMap();
		for(int i = 0; i < pixels.length; i++)
			if(pixels[i] > 0) { 
				add(fv, "P" + offset + "=" + sChar + i, true);
			}
		
		nodeFeatures[offset][nid][s] = fv.keys();
		Arrays.sort(nodeFeatures[offset][nid][s]);
		nodeFeatureVal[offset][nid][s] = new double[fv.size()];
	}

}
