package features;

import gnu.trove.TIntIntHashMap;
import java.util.Arrays;
import java.util.Locale;

import config.PosConfig;
import data.PosCorpus;
import data.RegexHelper;

public class PosSOPotentialFunction extends SecondOrderPotentialFunction {
	private PosCorpus myCorpus;
	private PosConfig config;
	
	public PosSOPotentialFunction(PosCorpus corpus, PosConfig config) {
		super(corpus);
		this.myCorpus = corpus;
		this.config = config;
		
		System.out.println(String.format(
				"Extract features from %d nodes and %d/%d states.", 
				numWordTypes, numTStates, numStates));
		
		for(int i = 0; i < numTStates; i++) {
			extractTransitionFeatures(i, S0, S00);
			extractTransitionFeatures(SN, i, S0);
			
			for(int j = 0; j < numTStates; j++) {
				extractTransitionFeatures(i, j, S0);
				extractTransitionFeatures(SN, i, j);
				
				for(int k = 0; k < numTStates; k++) {
					extractTransitionFeatures(i, j, k);
				}
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
	
	private void extractTransitionFeatures(int s, int sp, int spp) {
		String sppTag = myCorpus.getTag(spp);
		String spTag = myCorpus.getTag(sp);
		String sTag = myCorpus.getTag(s);
		
		TIntIntHashMap fv = new TIntIntHashMap();
		add(fv, "S-2=" + sppTag, true);
		add(fv, "S-1=" + spTag, true);
		add(fv, "S-0=" + sTag, true);
		add(fv, "S-10=" + spTag + " " + sTag, true); 
		add(fv, "S-20=" + sppTag + " " + sTag, true);
		add(fv, "S-1-2=" + sppTag + " " + spTag, true);
		add(fv, "S-1-20=" + sppTag + " " + spTag + " " + sTag, true);
		
		edgeFeatures[s][sp][spp] = fv.keys();
		Arrays.sort(edgeFeatures[s][sp][spp]);
		edgeFeatureVal[s][sp][spp] = new double[fv.size()];
	}

	private void extractEmissionFeatures(int offset, int nid, int s) {
		String tok = myCorpus.getWord(nid);
		String tag = myCorpus.getTag(s);
		String prefix = "P" + offset;
		Locale locale = new Locale(config.langName);
		
		TIntIntHashMap fv = new TIntIntHashMap();
		add(fv, prefix + "Tk=" + tok + " "  + tag, true);
		add(fv, prefix + "LTk=" + tok.toLowerCase(locale) + " " + tag, true);
		
		if(tok.toUpperCase(locale).charAt(0) == tok.charAt(0)) {
			add(fv, prefix + "Cap | " + tag, true);
		}
		
		if(RegexHelper.isNumerical(tok)) {
			add(fv, prefix + "Dig | " + tag, true);
		}
		if(RegexHelper.isPunctuation(tok)) {
			add(fv, prefix + "Pun | " + tag, true);
		}
		if(tok.indexOf('-') >= 0) {
			add(fv, prefix + "has=- | " + tag, true);
		}
		if(tok.indexOf('.')  >= 0) {
			add(fv, prefix + "has=. | " + tag, true);
		}
		
		int len = tok.length();
		for(int l = 2; l <= 4 && l < len; l++) {
			add(fv, prefix + "Suf=" + tok.substring(len - l, len) + " | " + tag, true);
		}
		
		nodeFeatures[offset][nid][s] = fv.keys();
		Arrays.sort(nodeFeatures[offset][nid][s]);
		nodeFeatureVal[offset][nid][s] = new double[fv.size()];
	}
}
