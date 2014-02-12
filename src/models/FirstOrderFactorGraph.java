package models;

import constraints.LatticeHelper;
import util.LogSummer;
import data.AbstractCorpus;
import data.AbstractSequence;
import features.FirstOrderPotentialFunction;

public class FirstOrderFactorGraph {
	int sequenceID, length, maxLength;//, numStates, numTStates;
	
	public double[][][] edgeScore;
	public double[][] nodeScore;
	
	public double[][][] edgeMarginal;
	public double[][] nodeMarginal;
	
	public double[][] alpha, beta, best;
	public int[][] prev;
	public int[] decode;
	public double logNorm;
	
	private int S0, SN;
	
	AbstractFactorIterator fiter;
	FirstOrderPotentialFunction potentialFunction;
	
	private double[] dpTemplate;
	
	
	public FirstOrderFactorGraph(AbstractCorpus corpus, FirstOrderPotentialFunction potentialFunction, 
			AbstractFactorIterator fiter) 
	{
		maxLength = corpus.maxSequenceLength + 1;
		int ns = corpus.numStates;
		
		edgeScore = new double[maxLength][ns][ns];
		nodeScore = new double[maxLength][ns];
		edgeMarginal = new double[maxLength][ns][ns];
		nodeMarginal = new double[maxLength][ns];
		alpha = new double[maxLength][ns];
		beta = new double[maxLength][ns];
		
		best = new double[maxLength][ns];
		prev = new int[maxLength][ns];
		decode = new int[maxLength];
		
		S0 = corpus.initialState;
		SN = corpus.finalState;
		
		this.potentialFunction = potentialFunction;
		this.fiter = fiter;
		
		dpTemplate = new double[maxLength * corpus.numStates * corpus.numStates];
	}
	
	public void fillScores(AbstractSequence sequence, double score)
	{
		set(sequence);
		double logScore = (score == 0 ? Double.NEGATIVE_INFINITY : Math.log(score));
		
		for(int i = 0; i <= length; i++) {
			for(int s : fiter.states(sequenceID, i)) {
				nodeScore[i][s] = logScore;
				for(int sp : fiter.states(sequenceID, i-1))
					edgeScore[i][s][sp] = logScore;
			}
		}
	}
	
	public void set(AbstractSequence sequence) 
	{
		this.length = sequence.length;
		this.sequenceID = sequence.seqID;
	}
	
	public void setScores(AbstractSequence sequence, double[][] nodeS, double[][][] edgeS)
	{
		set(sequence);
		this.nodeScore = nodeS;
		this.edgeScore = edgeS;
	}
	
	public void computeScores(AbstractSequence sequence, double[] parameters, double backoff) {
		set(sequence);

		for(int i = 0; i <= length; i++) {
			for(int s : fiter.states(sequenceID, i)) {
				nodeScore[i][s] = //LogSummer.sum(smoothing,
									backoff + 
						potentialFunction.computeNodeScore(sequenceID, i, s, parameters);
						
				for(int sp : fiter.states(sequenceID, i-1))
						edgeScore[i][s][sp] = //LogSummer.sum(smoothing,
									backoff + 
									potentialFunction.computeEdgeScore(sequenceID, i, s, sp, parameters);
			}
		}
	}
	
	public void backoff(double backoff) {
		double smo = Math.log(backoff);
		for(int i = 0; i <= length; i++) {
			for(int s : fiter.states(sequenceID, i)) {
				nodeScore[i][s] = LogSummer.sum(smo, nodeScore[i][s]);
				for(int sp : fiter.states(sequenceID, i-1))
						edgeScore[i][s][sp] = LogSummer.sum(smo, edgeScore[i][s][sp]); 
			}
		}
	}
	
	public void computeMarginals()
	{
		// do forward backward		
		for(int s : fiter.states(sequenceID, 0)) 
			alpha[0][s] = edgeScore[0][s][S0] + nodeScore[0][s]; 
		
		int len;
		
		for(int i = 1; i <= length; i++)
			for(int s : fiter.states(sequenceID, i)) {
				len = 0;
				for(int sp : fiter.states(sequenceID, i-1)) {  
					dpTemplate[len++] = alpha[i-1][sp] + edgeScore[i][s][sp] + nodeScore[i][s];
				}
				alpha[i][s] = LatticeHelper.logsum(dpTemplate, len);
			}
		
		logNorm = alpha[length][SN];	
		beta[length][SN] = 0;
		
		for(int i = length; i > 0; i--) 
			for(int sp : fiter.states(sequenceID, i - 1)) {
				len = 0;
				for(int s : fiter.states(sequenceID, i)) { 
					dpTemplate[len++] = beta[i][s] + edgeScore[i][s][sp] + nodeScore[i][s];
				}
				beta[i-1][sp] = LatticeHelper.logsum(dpTemplate, len);
			}
		
		for(int i = 0; i <= length; i++) 
			for(int s : fiter.states(sequenceID, i)) {
				nodeMarginal[i][s] = alpha[i][s] + beta[i][s] - logNorm;
				for(int sp : fiter.states(sequenceID, i - 1)) {
					edgeMarginal[i][s][sp] = (i == 0 ? nodeMarginal[0][s] : 
								alpha[i-1][sp] + beta[i][s] + edgeScore[i][s][sp] + nodeScore[i][s] - logNorm);
				}
			}
	}
	
	public double computeEntropy()
	{
		double ent = logNorm;
		
		for(int i = 0; i <= length; i++) 
			for(int s : fiter.states(sequenceID, i))  
				for(int sp : fiter.states(sequenceID, i-1)) {
					double marg = Math.exp(edgeMarginal[i][s][sp]);
					ent -=  marg * (edgeScore[i][s][sp] + nodeScore[i][s]);
				}
		
		return ent;
	}
	
	public void addToEmpirical(int sequenceID, int[] gold, double[] empirical) 
	{
		for(int i = 0; i <= gold.length; i++) {
			int s = (i == gold.length ? SN : gold[i]);
			int sp = (i == 0 ? S0 : gold[i-1]);
			potentialFunction.addToEmpirical(sequenceID, i, s, sp, empirical, 1.0);
		}
	}
	
	public void addToExpectation(int sequenceID, double[] empirical, double multiplier) 
	{
		for(int i = 0; i <= length; i++)
			for(int s : fiter.states(sequenceID, i))
				for(int sp : fiter.states(sequenceID, i-1)) {
					double marginal = Math.exp(edgeMarginal[i][s][sp]) * multiplier; 
					potentialFunction.addToEmpirical(sequenceID, i, s, sp, empirical, marginal);
				}
	}
	
	private void decodePosterior()
	{
		for(int i = 0; i < length; i++) {
			decode[i] = 0;
			double maxq = Double.NEGATIVE_INFINITY;

			for(int j : fiter.states(sequenceID, i)) {
				double q = Math.exp(nodeMarginal[i][j]);
				if(q > maxq) {
					decode[i] = j;
					maxq = q;
				}
			}
		}
	}
	
	public double decodeAndEvaluate(int[] gold) {
		decodePosterior();
		
		double accuracy = 0;
		for(int i = 0; i < length; i++) 
			if(gold[i] == decode[i]) ++ accuracy;
			
		return accuracy;
	}
}
