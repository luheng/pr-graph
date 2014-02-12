package analysis;

import data.AbstractCorpus;
import data.AbstractSequence;
import data.SparseSimilarityGraph;

public class GraphPropagationTester {
	private static int getMaxIndex(double[] p) {
		int maxi = 0;
		for(int i = 1; i < p.length; i++)
			if(p[maxi] < p[i]) maxi = i;
		return maxi;
	}
	
	private static void normalize(double[] p) {
		double norm = 0;
		for(int j = 0; j < p.length; j++) {
			if(p[j] < 0) p[j] = 0;
			norm += p[j];
		}
		for(int j = 0; j < p.length; j++) { 
			p[j] /= norm;
		}
	}
		
	public static int numIters;
	public static double stepSize;
	public static double stoppingCriteria = 1e-3;
	public static double devsAcc, testsAcc, totalAcc;
	private static int numFixedNodes, numTrainCovered, numTestCovered,
		numTestAll, numTestPunct;
	private static int numNodes, numLabels, numTokens;
	private static double[] qmax;
	
	private static AbstractCorpus corpus;
	private static SparseSimilarityGraph graph;
	public static double[][][] Q;
	public static NodeDistribution trains, tests;
	public static int[] fixed, pred;
	
	public static void runPropagation(AbstractCorpus corpus,
			SparseSimilarityGraph graph) {
		runPropagation(corpus, graph, 3000, 0.05, 1e-4);
	}
	
	private static void initializeTester() {
		numNodes = corpus.numNodes;
		numLabels = corpus.numTags;
		numFixedNodes = 0;
		numTrainCovered = 0;
		numTestCovered = numTestAll = numTestPunct = 0;
		numTokens = 0;
		
		trains = new NodeDistribution(numNodes, numLabels);
		tests = new NodeDistribution(numNodes, numLabels);
		
		Q = new double[2][numNodes][numLabels];
		fixed = new int[numNodes];
		pred = new int[numNodes];
		qmax = new double[numNodes];
	
		trains.initialize(corpus, corpus.trains);
		tests.initialize(corpus, corpus.tests);
		
		for (int i = 0; i < numNodes; i++) {
			if (trains.freq[i] > 0) {
				fixed[i] = 1;
				++ numFixedNodes;
				pred[i] = getMaxIndex(trains.dist[i]);
				qmax[i] = trains.dist[i][pred[i]];
				for (int j = 0; j < numLabels; j++) { 
					Q[0][i][j] = Q[1][i][j] = trains.dist[i][j];
				}
			}
			else {
				fixed[i] = 0;
				for (int j = 0; j < numLabels; j++) {
					Q[0][i][j] = Q[1][i][j] = 1.0 / numLabels;
				}
			}
		}
			
		for (int i = 0; i < corpus.numInstances; i++) {
			AbstractSequence instance = corpus.getInstance(i);
			int numCovered = 0, numPunct = 0;
			for (int j = 0; j < instance.length; j++) {
				if (instance.nodes[j] >= 0) {
					++ numCovered;
				}
				else if (numLabels == 12 && instance.tags[j] == 11) { 
					++ numPunct;
				}
			}
			if (instance.isLabeled) {
				numTrainCovered += numCovered;
			}
			else {
				numTestCovered += numCovered;
				numTestPunct += numPunct;
				numTestAll += instance.length;
			}
			numTokens += instance.length;
		}

		int numCoveredTokens = numTrainCovered + numTestCovered;
		System.out.println("test covered:\t" + numTestCovered +
				"\ttest punct:\t" +  numTestPunct + "\t" + "test all:\t"
				+ numTestAll);
		System.out.println(String.format(
				"Labeled sequences: %d\tNumber of nodes: %d\tfixed: %d" +
				" (%.2f%%)\nGraph covered tokens:\t %d(%.2f%%)\ntrains: %d" +
				" (%.2f%%)\ttests: %d\n",
				corpus.trains.length, numNodes, numFixedNodes,
				100.0 * numFixedNodes / numNodes, numCoveredTokens,
				100.0 * numCoveredTokens / numTokens, numTrainCovered,
				100.0 * numTrainCovered / numCoveredTokens, numTestCovered));
	}
	
	private static void runTester() {
		double prevNonSmoothness = 0;
		double prevTime = 1.0 * System.currentTimeMillis() / 1000;
		
		for (int iter = 0, curr = 0, next = 1; iter < numIters; iter ++,
				curr = 1 - curr, next = 1 - next) {
			double numTestsCorrect = 0;
			double nonSmoothness = 0;
			double eta = stepSize / (1.0 + Math.sqrt(iter));
			
			for (int i = 0; i < numNodes; i++) {
				if (fixed[i] == 0 && graph.edges[i].size() > 0) {
					for (int j = 0; j < numLabels; j++) {
						Q[next][i][j] = Q[curr][i][j];
					}
				}
				for (int j = 0; j < graph.edges[i].size(); j++) {
					int e = graph.edges[i].get(j);
					double w = graph.weights[i].get(j);
					if (w > 0) {
						for (int k = 0; k < numLabels; k++) {
							double grad = Q[curr][i][k] - Q[curr][e][k];
							if (fixed[i] == 0) {
								Q[next][i][k] -= eta * grad * w;
							}
							nonSmoothness += grad * grad * w;
						}
					}
				}
				if (fixed[i] == 0) {
					normalize(Q[next][i]);
					pred[i] = getMaxIndex(Q[next][i]);
				}
				if (tests.freq[i] > 0) {
					numTestsCorrect += tests.dist[i][pred[i]] * tests.freq[i];
				}
			}
			double smoChange = Math.abs(nonSmoothness - prevNonSmoothness) /
					prevNonSmoothness; 
			
			if (iter % 100 == 0 || iter == numIters - 1 ||
					Math.abs(smoChange) < stoppingCriteria) {
				testsAcc = 100.0 * (numTestPunct + numTestsCorrect) / numTestAll;
				
				double currTime = 1.0 * System.currentTimeMillis() / 1000;				
				System.out.println(String.format("iter::%d\teta::%f\t" +
						"acc tests::\t%.3f%%\tnon-smo::%f (%e)\t" +
						"used time::%.3f (sec)", 
						iter+1, eta, testsAcc, nonSmoothness / 2, smoChange,
						currTime - prevTime));
				
				prevTime = currTime;
 				if (smoChange < stoppingCriteria) {
 					break;
 				}
			}
			prevNonSmoothness = nonSmoothness;
		}
	}
	
	public static void runPropagation(AbstractCorpus corpus,
			SparseSimilarityGraph graph, int maxNumIterations, double stepSize,
			double stoppingCriteria) {
		GraphPropagationTester.corpus = corpus;
		GraphPropagationTester.graph = graph;
		GraphPropagationTester.numIters = maxNumIterations;
		GraphPropagationTester.stepSize = stepSize;
		GraphPropagationTester.stoppingCriteria = stoppingCriteria;
		
		initializeTester();
		runTester();
	}
}
