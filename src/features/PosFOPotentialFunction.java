package features;

import gnu.trove.TIntIntHashMap;

import java.util.Arrays;
import config.PosConfig;
import data.PosCorpus;
import data.RegexHelper;

public class PosFOPotentialFunction extends FirstOrderPotentialFunction {
	private PosCorpus myCorpus;
	
	public PosFOPotentialFunction(PosCorpus corpus, PosConfig config) {
		super(corpus);
		this.myCorpus = corpus;
		
		System.out.println(String.format("Extract features from %d nodes and %d/%d states.", 
				numWordTypes, numTStates, numStates));
		
		for(int i = 0; i < numTStates; i++) {
			extractTransitionFeatures(SN, i);
			extractTransitionFeatures(i, S0);
			for(int j = 0; j < numTStates; j++) {
				extractTransitionFeatures(i, j);
			}
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
	

	private void extractTransitionFeatures(int s, int sp) 
	{
		String spTag = myCorpus.getTag(sp);
		String sTag = myCorpus.getTag(s);
		
		TIntIntHashMap fv = new TIntIntHashMap();
		add(fv, "S-1=" + spTag, true);
		add(fv, "S-0=" + sTag, true);
		add(fv, "S-10=" + spTag + " " + sTag, true); 
		
		edgeFeatures[s][sp] = fv.keys();
		Arrays.sort(edgeFeatures[s][sp]);
		edgeFeatureVal[s][sp] = new double[fv.size()];
	}

	
	private void extractEmissionFeatures(int offset, int nid, int s) 
	{
		String tok = myCorpus.getWord(nid);
		String tag = myCorpus.getTag(s);
		String prefix = "P" + offset;
		
		TIntIntHashMap fv = new TIntIntHashMap();
		//add(fv, prefix + "Tg=" + tag, true);
		//add(fv, prefix + "Tk=" + tok + " "  + tag, true); // lowercased token
		add(fv, prefix + "LTk=" + tok.toLowerCase() + " "  + tag, true); // lowercased token
		
		if(tok.toUpperCase().charAt(0) == tok.charAt(0)) {
			//add(fv, prefix + "isCap", true);
			add(fv, prefix + "Cap=" + tag, true);  // is capitalized
		}
		
		if(RegexHelper.isNumerical(tok)) {
			//add(fv, prefix + "isDig", true);
			add(fv, prefix + "Dig=" + tag, true);
		}
		
		if(RegexHelper.isPunctuation(tok)) {
			//add(fv, prefix + "isPun", true);
			add(fv, prefix + "Pun=" + tag, true);
		}
		if(tok.indexOf('-') >= 0) {
			//add(fv, prefix + "has=-", true);
			add(fv, prefix + "has=- | " + tag, true);
		}
		if(tok.indexOf('.')  >= 0) {
			//add(fv, prefix + "Pun=.", true);
			add(fv, prefix + "has=. | " + tag, true);
		}
		
		int len = tok.length();
		for(int l = 2; l <= 4 && l < len; l++) {
			//add(fv, prefix + "Suf" + l + "=" + tok.substring(len - l, len), true);
			add(fv, prefix + "Suf=" + tok.substring(len - l, len) + " | " + tag, true);
		}
		
		nodeFeatures[offset][nid][s] = fv.keys();
		Arrays.sort(nodeFeatures[offset][nid][s]);
		nodeFeatureVal[offset][nid][s] = new double[fv.size()];
	}
}
