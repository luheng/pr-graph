package models;

import constraints.LatticeHelper;
import data.AbstractCorpus;
import data.AbstractSequence;
import features.SecondOrderPotentialFunction;

public class SecondOrderFactorGraph {
	protected int sequenceID, length, maxLength, numStates, numTStates;
	public double[][][][] edgeScore;
	public double[][] nodeScore;
	public double[][][][] edgeMarginal;
	public double[][] nodeMarginal;
	public double[][][] alpha, beta, best;
	public int[][][] prev;
	public int[] decode;
	public double logNorm;
	
	protected int S0, S00, SN;
	protected AbstractFactorIterator fiter;
	protected SecondOrderPotentialFunction potentialFunction;
	private double[] dpTemplate;
	
	public SecondOrderFactorGraph(AbstractCorpus corpus,
			SecondOrderPotentialFunction potentialFunction,
			AbstractFactorIterator fiter) {
		this.maxLength = corpus.maxSequenceLength + 1;
		this.numStates = corpus.numStates;
		this.numTStates = corpus.numTags;
		
		edgeScore = new double[maxLength][numStates][numStates][numStates];
		nodeScore = new double[maxLength][numStates];
		edgeMarginal = new double[maxLength][numStates][numStates][numStates];
		nodeMarginal = new double[maxLength][numStates];
		alpha = new double[maxLength][numStates][numStates];
		beta = new double[maxLength][numStates][numStates];
		
		S0 = corpus.initialState;
		S00 = corpus.initialStateSO;
		SN = corpus.finalState;
		
		this.potentialFunction = potentialFunction;
		this.fiter = fiter;
		dpTemplate = new double[maxLength * numStates * numStates];
	}
	
	public void set(AbstractSequence sequence) {
		this.length = sequence.length;
		this.sequenceID = sequence.seqID;
	}
	
	public void computeScores(AbstractSequence sequence, double[] parameters,
			double backoff) {
		set(sequence);
		for (int i = 0; i <= length; i++) {
			for (int s : fiter.states(sequenceID, i)) {
				nodeScore[i][s] = backoff + potentialFunction.computeNodeScore(
						sequenceID, i, s, parameters);
						
				for (int sp : fiter.states(sequenceID, i-1)) {
					for (int spp : fiter.states(sequenceID, i-2)) {
						edgeScore[i][s][sp][spp] = backoff +
								potentialFunction.computeEdgeScore(
										sequenceID, i, s, sp, spp, parameters);
					}
				}
			}
		}
	}
	
	public void computeMarginals() {
		for (int s : fiter.states(sequenceID, 0)) { 
			alpha[0][s][S0] = edgeScore[0][s][S0][S00] + nodeScore[0][s];
		}
		int len;
		for (int i = 1; i <= length; i++) {
			for (int s : fiter.states(sequenceID, i)) {
				for (int sp : fiter.states(sequenceID, i-1)) {
					len = 0;
					for (int spp : fiter.states(sequenceID, i-2)) {
						dpTemplate[len++] = alpha[i-1][sp][spp] +
								edgeScore[i][s][sp][spp] + nodeScore[i][s];
					}
					alpha[i][s][sp] = LatticeHelper.logsum(dpTemplate, len);
				}
			}
		}
		len = 0;
		for (int sp : fiter.states(sequenceID, length-1)) {
			beta[length][SN][sp] = 0;
			dpTemplate[len++] = alpha[length][SN][sp];
		}
		logNorm = LatticeHelper.logsum(dpTemplate, len);

		for (int i = length; i > 0; i--) { 
			for (int sp : fiter.states(sequenceID, i - 1)) { 
				for (int spp : fiter.states(sequenceID, i - 2)) {
					len = 0;
					for (int s : fiter.states(sequenceID, i)) {
						dpTemplate[len++] = beta[i][s][sp] +
								edgeScore[i][s][sp][spp] + nodeScore[i][s];
					}
					beta[i-1][sp][spp] = LatticeHelper.logsum(dpTemplate, len);
				}
			}
		}
		for (int s : fiter.states(sequenceID, 0)) {
			nodeMarginal[0][s] = edgeMarginal[0][s][S0][S00] =
					alpha[0][s][S0] + beta[0][s][S0] - logNorm;
		}
		for (int i = 1; i <= length; i++) {
			for (int s : fiter.states(sequenceID, i)) {
				len = 0;
				for (int sp : fiter.states(sequenceID, i - 1)) { 
					for (int spp : fiter.states(sequenceID, i - 2)) {
						edgeMarginal[i][s][sp][spp] = alpha[i-1][sp][spp] +
								beta[i][s][sp] + edgeScore[i][s][sp][spp] +
								nodeScore[i][s] - logNorm;
					}
					dpTemplate[len++] = alpha[i][s][sp] + beta[i][s][sp] -
							logNorm;
				}
				nodeMarginal[i][s] = LatticeHelper.logsum(dpTemplate, len);
			}
		}
	}
	
	public void addToEmpirical(int sequenceID, int[] gold, double[] empirical) {
		for (int i = 0; i <= gold.length; i++) {
			int s = (i == gold.length ? SN : gold[i]);
			int sp = (i == 0 ? S0 : gold[i-1]);
			int spp = (i == 0 ? S00 : (i == 1 ? S0 : gold[i-2]));
			potentialFunction.addToEmpirical(sequenceID, i, s, sp, spp,
					empirical, 1.0);
		}
	}
	
	public void addToExpectation(int sequenceID, double[] empirical,
			double multiplier) {
		for (int i = 0; i <= length; i++) {
			for (int s : fiter.states(sequenceID, i)) {
				for (int sp : fiter.states(sequenceID, i-1)) {
					for (int spp : fiter.states(sequenceID, i-2)) {
						if (!Double.isInfinite(edgeMarginal[i][s][sp][spp])) {
							double marginal = Math.exp(
									edgeMarginal[i][s][sp][spp]) * multiplier;
							potentialFunction.addToEmpirical(sequenceID, i,
									s, sp, spp, empirical, marginal);
						}
					}
				}
			}
		}
	}
	
	private void decodePosterior() {
		for (int i = 0; i < length; i++) {
			decode[i] = 0;
			double maxq = Double.NEGATIVE_INFINITY;
			for (int j : fiter.states(sequenceID, i)) {
				double q = Math.exp(nodeMarginal[i][j]);
				if (q > maxq) {
					decode[i] = j;
					maxq = q;
				}
			}
		}
	}
	
	public double decodeAndEvaluate(int[] gold) {
		if (best == null) {
			best = new double[maxLength][numStates][numStates];
			prev = new int[maxLength][numStates][numStates];
			decode = new int[maxLength];
		}
		decodePosterior();
		double accuracy = 0;
		for (int i = 0; i < length; i++) { 
			if (gold[i] == decode[i]) ++ accuracy;
		}
		return accuracy;
	}
}
