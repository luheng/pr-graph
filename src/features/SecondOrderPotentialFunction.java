package features;

import data.AbstractCorpus;
import data.AbstractSequence;
import gnu.trove.TIntIntHashMap;
import gnu.trove.TObjectIntHashMap;

public class SecondOrderPotentialFunction {
	protected AbstractCorpus corpus;
	protected int[][][][] edgeFeatures; // state-id , prev-state
	protected int[][][][] nodeFeatures; // node-id, state-id
	
	protected double[][][][] edgeFeatureVal;
	protected double[][][][] nodeFeatureVal;
	
	protected TObjectIntHashMap<String> feature2index;
	protected TIntIntHashMap index2count;
	
	protected int numStates, numTStates, numWordTypes, S0, S00, SN;
	
	protected SecondOrderPotentialFunction(AbstractCorpus corpus) 
	{
		this.corpus = corpus;
		this.numWordTypes = corpus.numWords;
		this.numStates = corpus.numStates;
		this.numTStates = corpus.numTags;
		
		S0 = corpus.initialState;
		S00 = corpus.initialStateSO;
		SN = corpus.finalState;
		
		System.out.println("Initial state:\t" + S0);
		System.out.println("Initial state 2nd order:\t" + S00);
		System.out.println("Final state:\t" + SN);
		
		feature2index = new TObjectIntHashMap<String>();
		index2count = new TIntIntHashMap();
	
		edgeFeatures = new int[numStates][numStates][numStates][];
		nodeFeatures = new int[3][numWordTypes][numStates][];
		
		edgeFeatureVal = new double[numStates][numStates][numStates][];
		nodeFeatureVal = new double[3][numWordTypes][numStates][];
	}
	
	protected void assignFeatureValues(boolean rescaleFeatures)
	{
		double transitionFeatureNorm = 0, emissionFeatureNorm = 0;
		// get estimation of feature counts on training data
		for(int sid : corpus.trains) {
			AbstractSequence instance = corpus.getInstance(sid);
			int[] tags = instance.tags;
			int[] toks = instance.tokens;
			
			for(int i = 0; i <= instance.length; i++) {
				int s = (i == instance.length ? SN : tags[i]);
				int sp = (i == 0 ? S0 : tags[i-1]);
				int spp = (i == 0 ? S00 : (i == 1 ? S0 : tags[i-2]));
				
				if(i > 0) {
					emissionFeatureNorm += nodeFeatures[0][toks[i-1]][s].length;
				}
				if(i < instance.length) {
					emissionFeatureNorm += nodeFeatures[1][toks[i]][s].length;
				}
				if(i < instance.length - 1) {
					emissionFeatureNorm += nodeFeatures[2][toks[i+1]][s].length;
				}
				transitionFeatureNorm += edgeFeatures[s][sp][spp].length;
			}
		}
		// assign feature values
		System.out.println(String.format("\nExtracted %d features." +
				" #Transiton:\t%d\t#Emission:\t%d", 
				feature2index.size(), (int) transitionFeatureNorm,
				(int) emissionFeatureNorm));
		
		double scaling = 1.0;
		if(rescaleFeatures) {
			scaling =  emissionFeatureNorm / transitionFeatureNorm;
			System.out.println("divide emission feature values by:\t" + scaling);
		}
		
		for(int i = 0; i < numTStates; i++) {
			fill(edgeFeatureVal[i][S0][S00], 1.0);
			fill(edgeFeatureVal[SN][i][S0], 1.0);
			for(int j = 0; j < numTStates; j++) {
				fill(edgeFeatureVal[i][j][S0], 1.0);
				fill(edgeFeatureVal[SN][i][j], 1.0);
				
				for(int k = 0; k < numTStates; k++) {
					fill(edgeFeatureVal[i][j][k], 1.0);
				}
			}
		}
		
		for(int i = 0; i < numWordTypes; i++) {
			fill(nodeFeatureVal[0][i][SN], 1.0 / scaling);
			fill(nodeFeatureVal[2][i][S0], 1.0 / scaling);
			for(int j = 0; j < numTStates; j++) {
				fill(nodeFeatureVal[0][i][j], 1.0 / scaling);
				fill(nodeFeatureVal[1][i][j], 1.0 / scaling);
				fill(nodeFeatureVal[2][i][j], 1.0 / scaling);
			}
		}
	}

	private void fill(double[] fv, double d) {
		for(int i = 0; i < fv.length; i++) fv[i] = d;
	}

	
	protected void add(TIntIntHashMap fv, String feature, boolean update) {
		int fid = -1;
		if(feature2index.contains(feature)) {
			fid = feature2index.get(feature);
		}
		else if(update) {
			fid = feature2index.size();
			feature2index.put(feature, fid);
		}
		if(fid >= 0) {
			fv.adjustOrPutValue(fid, 1, 1);
			index2count.adjustOrPutValue(fid, 1, 1);
		}
	}

	public int getNumFeatures() {
		return feature2index.size();
	}
	
	public double computeScore(int sid, int pos, int s, int sp, int spp, double[] parameters)
	{	
		return computeNodeScore(sid, pos, s, parameters) + computeEdgeScore(sid, pos, s, sp, spp, parameters);
	}
	
	public double computeEdgeScore(int sid, int pos, int s, int sp, int spp, double[] parameters)
	{
		double r = 0;
		int[] fid = edgeFeatures[s][sp][spp];
		
		if(fid != null) {
			double[] fv = edgeFeatureVal[s][sp][spp];
			for(int j = 0; j < fid.length; j++) r += parameters[fid[j]] * fv[j];
		}
	
		return r;
	}

	public double computeNodeScore(int sid, int pos, int s, double[] parameters)
	{
		double r = 0;

		int[] fid;
		double[] fv;
		int[] toks = corpus.getInstance(sid).tokens;
		
		if(pos > 0) {
			fid = nodeFeatures[0][toks[pos-1]][s];
			if(fid != null) {
				fv = nodeFeatureVal[0][toks[pos-1]][s];
				for(int j = 0; j < fid.length; j++) r += parameters[fid[j]] * fv[j];
			}
		}
		
		if(pos < toks.length) {
			fid = nodeFeatures[1][toks[pos]][s];
			if(fid != null) {
				fv = nodeFeatureVal[1][toks[pos]][s];
				for(int j = 0; j < fid.length; j++) r += parameters[fid[j]] * fv[j];
			}
		}
		
		if(pos + 1 < toks.length) {
			fid = nodeFeatures[2][toks[pos+1]][s];
			if(fid != null) {
				fv = nodeFeatureVal[2][toks[pos+1]][s];
				for(int j = 0; j < fid.length; j++) r += parameters[fid[j]] * fv[j];
			} 
		}
		
		return r;
	}

	public void addToEmpirical(int sid, int pos, int s, int sp, int spp, double[] empirical, double marginal) 
	{	
		if(marginal == 0 || Double.isInfinite(marginal) || Double.isNaN(marginal)) return;
		
		double[] fv;
		int[] toks = corpus.getInstance(sid).tokens;
		
		int[] fid = edgeFeatures[s][sp][spp];
		if(fid != null) {
			fv = edgeFeatureVal[s][sp][spp];
			for(int j = 0; j < fid.length; j++) empirical[fid[j]] += marginal * fv[j];
		}
		
		if(pos > 0) { 
			fid = nodeFeatures[0][toks[pos-1]][s];
			if(fid != null) {
				fv = nodeFeatureVal[0][toks[pos-1]][s];
				for(int j = 0; j < fid.length; j++) empirical[fid[j]] += marginal * fv[j];
			}
		}
		
		if(pos < toks.length) {
			fid = nodeFeatures[1][toks[pos]][s];
			if(fid != null) {
				fv = nodeFeatureVal[1][toks[pos]][s];
				for(int j = 0; j < fid.length; j++) empirical[fid[j]] += marginal * fv[j];
			}
		}
		
		if(pos + 1 < toks.length) {
			fid =  nodeFeatures[2][toks[pos+1]][s];
			if(fid != null) {
				fv = nodeFeatureVal[2][toks[pos+1]][s];
				for(int j = 0; j < fid.length; j++) empirical[fid[j]] += marginal * fv[j];
			} 
		}
	}
	
}
