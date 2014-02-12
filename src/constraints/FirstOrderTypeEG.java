package constraints;

import java.util.Arrays;
import models.AbstractFactorIterator;
import models.FirstOrderFactorGraph;
import config.Config;
import data.AbstractCorpus;
import data.AbstractSequence;
import data.SparseSimilarityGraph;
import features.FirstOrderPotentialFunction;
import gnu.trove.TIntArrayList;

public class FirstOrderTypeEG {
	double[][][] nodeMarginal, nodeScore, mstepNodeScore;
	double[] theta, primalVars;
	
	double[][] nodeDist; // node-id x labels
	double[] nodeFreq;
	
	int[][] decoded;
	int[] unlabeled;
	int[][] sentenceBatches;
	int numUnlabeled;
	
	AbstractCorpus corpus;
	SparseSimilarityGraph graph;
	FirstOrderPotentialFunction ffunc;
	Config config;
	AbstractFactorIterator fiter;

	double uniformInit;
	double eta0, eta;
	int S0, S00, SN;
	int numIterations, currIter;
	int numNodes, numSequences, numStates, numFeatures;
	int numThreads;
	
	SentenceUpdateThread[] uthreads;
	SentenceMonitorThread[] mthreads;
	EmpiricalCountThread[] ethreads;

	public double lpStrength;
	public double entropyObjective, likelihoodObjective, graphObjective, objective, gradientNorm, objChange;
	public double[] softEmpiricalCounts;
	
	double stoppingCriteria;
	double goldViolation;
	double prevObjective;
	
	boolean finalRound, succeed;
	
	public FirstOrderTypeEG(AbstractCorpus corpus, SparseSimilarityGraph graph, FirstOrderPotentialFunction ffunc, 
			AbstractFactorIterator fiter, Config config)
	{
		this.corpus = corpus;
		this.graph = graph;
		this.ffunc = ffunc;
		this.config = config;
		this.fiter = fiter;
		
		this.numNodes = corpus.numNodes;
		this.numSequences = corpus.numInstances;
		this.numStates = corpus.numTags;
		this.S0 = corpus.initialState;
		this.SN = corpus.finalState;
		
		this.numFeatures = ffunc.getNumFeatures();
		
			
		this.numThreads = config.numThreads;
		this.uniformInit = config.estepInit;
		this.lpStrength = config.graphRegularizationStrength;
		this.stoppingCriteria = config.estepStopThreshold;
		
		this.numIterations = config.numEstepIters;
		this.currIter = 0;
		
		System.out.println("Initializing EGD Contraint trainer ... ");
		System.out.println("Number of nodes:\t" + numNodes + "\tNumber of states\t" + numStates);

		goldViolation = graph.computeGoldViolation(corpus);
		System.out.println("Gold violation::\t" + goldViolation);
		
		nodeScore = new double[numSequences][][];
		mstepNodeScore = new double[numSequences][][];
		nodeMarginal = new double[numSequences][][];
		decoded = new int[numSequences][];
		nodeDist = new double[numNodes][numStates];
		nodeFreq = new double[numNodes];
		primalVars = new double[numFeatures];
		
		softEmpiricalCounts = new double[numFeatures];
		
		unlabeled = corpus.tests;
		numUnlabeled = unlabeled.length;
		System.out.println("EG projection on " + numUnlabeled + " instances.");
		
		uthreads = new SentenceUpdateThread[numThreads];
		mthreads = new SentenceMonitorThread[numThreads];
		ethreads = new EmpiricalCountThread[numThreads];
		sentenceBatches = new int[numThreads][];
		
		for(int i = 0; i < numThreads; i++) {
			int bsize = unlabeled.length / numThreads;
			TIntArrayList sids = new TIntArrayList();
			for(int j = i * bsize; j < (i + 1 < numThreads ? (i + 1) * bsize : unlabeled.length); j++) {
				sids.add(unlabeled[j]);
			}
			sentenceBatches[i] = sids.toNativeArray();
		}
		
		this.stoppingCriteria = config.estepStopThreshold;
		System.out.println("EG stopping threshold:\t" + stoppingCriteria);
	}
	
	public boolean project(double[] theta, double likelihood) throws InterruptedException
	{	
		this.theta = theta;
		this.likelihoodObjective = likelihood;
		objective = prevObjective = likelihoodObjective - entropyObjective + lpStrength / 2 * graphObjective;
		
		if(currIter == 0) {
			System.out.println("Initializing E-step Constraint ...");
			eta0 = config.initialLearningRate;
			eta = eta0;	
			currIter = 0;
			prevObjective = Double.NEGATIVE_INFINITY;
			initializeCounts(theta);
			
			for(int i = 0; i < numThreads; i++) 
				mthreads[i] = new SentenceMonitorThread(sentenceBatches[i]);
			for(int i = 0; i < numThreads; i ++) mthreads[i].start();
			for(int i = 0; i < numThreads; i ++) mthreads[i].join();
			
			updateNodeDist();
			updateObjective();
		}
		else {
			FirstOrderFactorGraph model = new FirstOrderFactorGraph(corpus,
					ffunc, fiter);
			for(int sid : unlabeled) {
				AbstractSequence instance = corpus.getInstance(sid);
				model.computeScores(instance, theta, config.backoff);	
				for(int i = 0; i <= instance.length; i++)
					for(int s : fiter.states(sid, i)) 
						mstepNodeScore[sid][i][s] = model.nodeScore[i][s];
			}
			System.out.println("Cached node scores.");
		}
		
		succeed = false;
		finalRound = false;
		
		for(int iter = 0; iter < numIterations; iter ++) 
		{
			eta = eta0 / (1.0 + iter);	
			for(int i = 0; i < numThreads; i++) {
				uthreads[i] = new SentenceUpdateThread(sentenceBatches[i]);
				mthreads[i] = new SentenceMonitorThread(sentenceBatches[i]);
			}
		
			/*** update dual variables ***/
			for(int i = 0; i < numThreads;i ++) uthreads[i].start();
			for(int i = 0; i < numThreads;i ++) uthreads[i].join();
			
			gradientNorm = 0;
			for(int i = 0; i < numThreads;i ++) {
				gradientNorm += uthreads[i].gradientNorm;
				uthreads[i] = null;
			}
			
			/*** update primal variables ***/
			for(int i = 0; i < numFeatures; i++) { 
				primalVars[i] = (1 - eta) * primalVars[i] + eta * theta[i];
			}
	
			/*** update marginals  ***/
			for(int i = 0; i < numThreads;i ++) mthreads[i].start();
			for(int i = 0; i < numThreads;i ++) mthreads[i].join();
			
			updateNodeDist();
			updateObjective();
			
			if(currIter > 0 && objective > prevObjective) {
				System.out.println("objective not improving, stop EGD.");
				break;
			}
			if(iter > 0 && objChange < stoppingCriteria) {
				succeed = true;
				break;
			}
			
			prevObjective = objective;
			currIter ++;
		}
		
		prevObjective = objective;
		finalRound = true;
		
		/****** compute gradient *******/
		finalizeEStep();
		
		System.out.println("EDG Finished. succeed:\t" + succeed);
		return succeed;
	}

	private void initializeCounts(double[] theta) {
		FirstOrderFactorGraph model = new FirstOrderFactorGraph(corpus, ffunc, fiter);		
		Arrays.fill(nodeFreq, 0.0);
		
		for(int sid = 0; sid < numSequences; sid++) {
			AbstractSequence instance = corpus.getInstance(sid);
			int length = instance.length;
			
			nodeScore[sid] = new double[length + 1][corpus.numStates];
			mstepNodeScore[sid] = new double[length + 1][corpus.numStates];
			nodeMarginal[sid] = new double[length + 1][corpus.numStates];
			decoded[sid] = new int[length]; 
		
			if(instance.isLabeled) {
				for(int i = 0; i < length; i++)
					for(int s : fiter.states(sid, i)) {
						nodeMarginal[sid][i][s] = (s == instance.tags[i]) ? 1 : 0;
					nodeMarginal[sid][length][SN] = 1;
				}
			}	
			else {
				model.computeScores(instance, theta, config.backoff);	
				for(int i = 0; i <= length; i++)
					for(int s : fiter.states(sid, i)) {
						mstepNodeScore[sid][i][s] = model.nodeScore[i][s];
						nodeScore[sid][i][s] = mstepNodeScore[sid][i][s];
					}
			}
			
			for(int nid : instance.nodes) {
				if(nid >= 0) nodeFreq[nid] ++;
			}
		}
				
		for(int i = 0; i < theta.length; i++) {
			primalVars[i] = theta[i];
		}
	}
	
	private double computeGraphViolation()
	{
		double gv = 0;
		for(int sid = 0; sid < numSequences; sid++) {
			AbstractSequence instance = corpus.getInstance(sid);
			for(int t = 0; t < instance.length; t++) {
				int nid = instance.nodes[t];
				if(nid < 0) continue;
				for(int k = 0; k < numStates; k++) {
					for(int j = 0; j < graph.edges[nid].size(); j++) {
						int e = graph.edges[nid].get(j);
						double w = graph.weights[nid].get(j) / nodeFreq[nid];
						gv += w * nodeDist[nid][k] * (nodeDist[nid][k] - nodeDist[e][k]);
					}
				}
			}
		}
		return gv;
	}
	
	private void updateObjective()
	{
		entropyObjective = 0;
		likelihoodObjective = 0;
		graphObjective = computeGraphViolation();
		
		for(int i = 0; i < numThreads; i++) {
			entropyObjective += mthreads[i].localEntropy;
			likelihoodObjective += mthreads[i].localLikelihood;
		}
		
		objective = likelihoodObjective - entropyObjective + lpStrength / 2 * graphObjective;
		objChange = Math.abs(objective - prevObjective) / prevObjective;
		
		System.out.print(" ... " + (currIter + 1) + "\t");
		System.out.println("Negative Likelihood:\t" + likelihoodObjective + "\tEntropy:\t" + entropyObjective + 
				"\tGraph Violation:\t" + graphObjective + 
				"\tCombined objective:\t" + objective + "\tchange:\t" + objChange);
		System.out.println(" gradient norm " + gradientNorm + " ... current eta: " + eta);
	}
	
	private void finalizeEStep() throws InterruptedException
	{
		double avgent = 0, avgmaxq = 0, avgstd = 0;
		double maxmaxq = Double.NEGATIVE_INFINITY, minmaxq = Double.POSITIVE_INFINITY;
		double mines = Double.POSITIVE_INFINITY, maxes = Double.NEGATIVE_INFINITY;
		double minns = Double.POSITIVE_INFINITY, maxns = Double.NEGATIVE_INFINITY;
		double acc = 0, norm = 0;
		
		for(int i = 0; i < numThreads; i++) { 
			ethreads[i] = new EmpiricalCountThread(sentenceBatches[i], 
				new FirstOrderEGDMonitor(corpus.numStates, fiter));
		}
		for(int i = 0; i < numThreads;i ++) ethreads[i].start();
		for(int i = 0; i < numThreads;i ++) ethreads[i].join();
		
		Arrays.fill(softEmpiricalCounts, 0);
		
		for(int i = 0; i < numThreads; i++) {
			FirstOrderEGDMonitor monitor = ethreads[i].monitor;
			acc += monitor.numCorrect;
			norm += monitor.numTotal;
			avgent += monitor.avgent;
			avgmaxq += monitor.avgmaxq;
			avgstd += monitor.avgstd;
			maxmaxq = Math.max(maxmaxq, monitor.maxmaxq);
			minmaxq = Math.min(minmaxq, monitor.minmaxq);
		
			minns = Math.min(minns, monitor.nsrange[0]);
			maxns = Math.max(maxns, monitor.nsrange[1]);
			mines = Math.min(mines, monitor.esrange[0]);
			maxes = Math.max(maxes, monitor.esrange[1]);
			
			for(int j = 0; j < numFeatures; j++)
				softEmpiricalCounts[j] += ethreads[i].localSoftEmpiricals[j];
			
			ethreads[i] = null;
		}
		
		System.out.print(" ... finalizing e-step ... \t");
		System.out.println("Accuracy::\t" + acc / norm + "\tAvg entropy::\t" + avgent / norm + "\tAvg std::\t" + avgstd / norm);
		System.out.println("min max q::\t" + minmaxq + "\tmaxmaxq::\t" + maxmaxq + "\tavgmaxq::\t" + avgmaxq / norm);
		System.out.println("edge score range::\t" + mines + " - " + maxes + "\tnode score range::\t" + minns + " - " + maxns);
	}
	
	
	private void updateNodeDist()
	{
		LatticeHelper.deepFill(nodeDist, 0);
		for(int sid = 0; sid < corpus.numInstances; sid++) {
			AbstractSequence instance = corpus.getInstance(sid);
			for(int i = 0; i < instance.length; i++) {
				int nid = instance.nodes[i];
				if(nid >= 0)
					for(int s : fiter.states(sid, i)) 
						nodeDist[nid][s] += nodeMarginal[sid][i][s];
			}
		}
		
		for(int nid = 0; nid < numNodes; nid++) {
			for(int s = 0; s < numStates; s++)
				if(nodeFreq[nid] > 0) 
					nodeDist[nid][s] /= nodeFreq[nid];
		}
	}

	public void projectScores(AbstractSequence instance, FirstOrderFactorGraph model)
	{
		model.set(instance);
		
		int length = instance.length;
		int sid = instance.seqID;
		
		for(int i = 0; i <= length; i++)
			for(int s : fiter.states(sid, i)) {
				model.nodeScore[i][s] = nodeScore[sid][i][s];
				for(int sp : fiter.states(sid, i-1)) { 
					model.edgeScore[i][s][sp] =
						ffunc.computeEdgeScore(instance.seqID, i, s, sp, primalVars);
				}
			}
	}
	
	public int[] getDecoded(int sid) 
	{
		return decoded[sid];
	}

	
	class SentenceUpdateThread extends Thread 
	{
		int[] sentenceIDs;
		double gradientNorm;
		
		public SentenceUpdateThread(int[] sentenceIDs) {
			this.sentenceIDs = sentenceIDs;
			this.gradientNorm = 0;
		}
		
		@Override
		public void run() {
			double grad;
			for(int sid : sentenceIDs) {
				AbstractSequence instance = corpus.getInstance(sid);						
				for(int i = 0; i <= instance.length; i++) 
					for(int s : fiter.states(sid, i)) {
						nodeScore[sid][i][s] -= eta * (grad = getNodeGradient(sid, i, s));
						gradientNorm += grad * grad;
					}			
			}
		}
		
		private double getNodeGradient(int sid, int i, int s)
		{
			double gradA = nodeScore[sid][i][s] - mstepNodeScore[sid][i][s];
			double gradB = 0;
			
			if(s != SN && s != S0) { 
				int nid = corpus.getInstance(sid).nodes[i];
				if(nid >= 0) {
					for(int j = 0; j < graph.edges[nid].size(); j++) {
						int e = graph.edges[nid].get(j);  
						double w = graph.weights[nid].get(j) / nodeFreq[nid];
						gradB += w * (nodeDist[nid][s] - nodeDist[e][s]);
					}
				}
			}

			return gradA + 2 * lpStrength * gradB;
		}
	}
	
	class SentenceMonitorThread extends Thread 
	{
		int[] sentenceIDs;

		FirstOrderFactorGraph model, projectedModel;
		
		double localEntropy, localLikelihood;
		double[] localSoftEmpiricals;
		
		public SentenceMonitorThread(int[] sentenceIDs) {
			this.sentenceIDs = sentenceIDs;
			this.model = new FirstOrderFactorGraph(corpus, ffunc, fiter);
			this.projectedModel = new FirstOrderFactorGraph(corpus, ffunc, fiter);
			
			localSoftEmpiricals = new double[numFeatures];
		}
		
		@Override
		public void run() {
			localEntropy = 0;
			localLikelihood = 0;
			Arrays.fill(localSoftEmpiricals, 0);
			
			for(int sid : sentenceIDs) {
				AbstractSequence instance = corpus.getInstance(sid);
				
				model.computeScores(instance, theta, config.backoff);
				model.computeMarginals();
				
				projectScores(instance, projectedModel);
				projectedModel.computeMarginals();
				projectedModel.addToExpectation(sid, localSoftEmpiricals, 1.0);
				
				localEntropy += projectedModel.logNorm;
				localLikelihood += model.logNorm;
				
				for(int i = 0; i <= instance.length; i++) 
					for(int s : fiter.states(sid, i)) { 
						nodeMarginal[sid][i][s] = Math.exp(projectedModel.nodeMarginal[i][s]); // update marginal
						for(int sp : fiter.states(sid, i-1)) {
								double fmar = Math.exp(projectedModel.edgeMarginal[i][s][sp]);
								localEntropy -=  fmar * (projectedModel.edgeScore[i][s][sp] + projectedModel.nodeScore[i][s]);
								localLikelihood -= fmar * (model.edgeScore[i][s][sp] + model.nodeScore[i][s]);
						}
					}
				
			}
		}
		
		
	}
	
	class EmpiricalCountThread extends Thread 
	{
		int[] sentenceIDs;
		FirstOrderEGDMonitor monitor;
		FirstOrderFactorGraph projectedModel;
		double[] localSoftEmpiricals;
		
		public EmpiricalCountThread(int[] sentenceIDs, FirstOrderEGDMonitor monitor) {
			this.sentenceIDs = sentenceIDs;
			this.monitor = monitor;
			this.projectedModel = new FirstOrderFactorGraph(corpus, ffunc, fiter);
			localSoftEmpiricals = new double[numFeatures];
		}
		
		@Override
		public void run() {
			Arrays.fill(localSoftEmpiricals, 0);
			
			for(int sid : sentenceIDs) {
				AbstractSequence instance = corpus.getInstance(sid);
				projectScores(instance, projectedModel);
				projectedModel.computeMarginals();
				projectedModel.addToExpectation(sid, localSoftEmpiricals, 1.0);
				
				monitor.count(instance, projectedModel, decoded[sid]);
			}
		}
	}
	
}
