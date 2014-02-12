package trainers;

import java.util.Arrays;
import analysis.GeneralTimer;
import analysis.GraphPropagationTester;
import models.AbstractFactorIterator;
import models.SecondOrderFactorGraph;
import config.Config;
import constraints.SecondOrderTypeEG;
import optimization.gradientBasedMethods.LBFGS;
import optimization.gradientBasedMethods.Objective;
import optimization.gradientBasedMethods.Optimizer;
import optimization.gradientBasedMethods.stats.OptimizerStats;
import optimization.linesearch.*;
import optimization.stopCriteria.AverageValueDifference;
import optimization.stopCriteria.CompositeStopingCriteria;
import data.AbstractCorpus;
import data.AbstractSequence;
import data.SparseSimilarityGraph;
import features.SecondOrderPotentialFunction;


public class GraphBootstrappedTrainer {
	AbstractCorpus corpus;
	SecondOrderPotentialFunction potentialFunction;
	AbstractFactorIterator fiter;
	SecondOrderFactorGraph model;
	SparseSimilarityGraph graph;
	SecondOrderTypeEG constraint;
	MStepObjective mstepObjective;
	
	int numParameters;
	double[] oldTheta, theta;
	double[] empiricalCounts, expectedCounts, softEmpiricalCounts;

	Config config;
	boolean transductive, doAnalysis;
	double unlabeledDiscount = 1.0, labeledStrength;
	
	int currIter;
	public double trainAcc, devAcc, testAcc;
	
	// LBFG-S stuff
	LineSearchMethod lineSearch;
	CompositeStopingCriteria stopping;
	Optimizer optimizer;
	OptimizerStats stats;
	double prevStepSize;
	
	GeneralTimer timer;
	int[][] hardDecoding;
	
	public GraphBootstrappedTrainer(AbstractCorpus corpus,
			SecondOrderPotentialFunction potentialFunction,
			SparseSimilarityGraph graph, AbstractFactorIterator fiter,
			Config config) {
		this.corpus = corpus;
		this.potentialFunction = potentialFunction;
		this.fiter = fiter;
		this.graph = graph;
		this.config = config;
	}
	
	private void initializeModel()
	{
		numParameters = potentialFunction.getNumFeatures();
		theta = new double[numParameters];
		oldTheta = new double[numParameters];
		empiricalCounts = new double[numParameters];
		expectedCounts = new double[numParameters];
		softEmpiricalCounts = new double[numParameters];
		
		hardDecoding = new int[corpus.numInstances][];
		for(int sid = 0; sid < corpus.numInstances; sid++) {
			AbstractSequence instance = corpus.getInstance(sid);
			hardDecoding[sid] = new int[instance.length];
		}
		prevStepSize = 1.0;
		
		Arrays.fill(empiricalCounts, 0.0);
		model = new SecondOrderFactorGraph(corpus, potentialFunction, fiter);
		constraint = new SecondOrderTypeEG(corpus, graph, potentialFunction,
				fiter, config);
		
		double nrLabeledTokens = 0, nrUnlabeledTokens = 0;
		for(int tid = 0; tid < corpus.numInstances; tid++) {
			AbstractSequence instance = corpus.getInstance(tid);
			if(instance.isLabeled) {
				model.addToEmpirical(tid, instance.tags, empiricalCounts);
				nrLabeledTokens += instance.length;
			}
			else nrUnlabeledTokens += instance.length;
		}
		
		unlabeledDiscount = nrUnlabeledTokens / nrLabeledTokens;
		labeledStrength = config.labelStrength;
		System.out.println("Number of labeled tokens:\t" + nrLabeledTokens +
				"\tUnlabeled:\t" + nrUnlabeledTokens +
				"\tUnlabeled discount factor:\t" + unlabeledDiscount);
		System.out.println("Labeled strength:\t" + labeledStrength);
		
		timer = new GeneralTimer();
	}
	
	
	private double corpusMStep()
	{
		System.out.println("Corpus M Step");
		timer.stamp("mstep-start");
		
		lineSearch = new WolfRuleLineSearch(new InterpolationPickFirstStep(
				prevStepSize), 1e-4, 0.9, 10);
		lineSearch.setDebugLevel(0);
	
		stopping = new CompositeStopingCriteria();
		stopping.add(new AverageValueDifference(config.mstepStopThreshold));
		
		optimizer = new LBFGS(lineSearch, 10);
		optimizer.setMaxIterations(config.numMstepIters);
		stats = new OptimizerStats();
		
		mstepObjective = new MStepObjective(theta, transductive); 
		boolean succeed = optimizer.optimize(mstepObjective, stats, stopping);
		prevStepSize = optimizer.getCurrentStep();
		System.out.println("success:\t" + succeed +
				"\twith latest stepsize:\t" + prevStepSize);
	
		
		double obj = mstepObjective.objective;
		if(constraint != null) obj += constraint.lpStrength / 2 *
				constraint.graphObjective  - constraint.entropyObjective;
				
		System.out.println("After M-step of iteration::\t" + currIter);
		System.out.println("Negative Labeled Likelihood::\t" +
				mstepObjective.labelLikelihood);
		System.out.println("Negative Unlabeled Likelihood::\t" +
				mstepObjective.softLikelihood);
		System.out.println("*** Combined objective::\t" + obj);
		
		timer.stamp("mstep-end");
		timer.printIntervalsInMins("mstep-start", "mstep-end");
		return obj;
	}
	
	double corpusEStep()
    {
        System.out.println("Corpus E Step");
        timer.stamp("estep-start");

        try {
            constraint.project(theta, mstepObjective.softLikelihood);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        double obj = constraint.objective;
        if(mstepObjective != null) {
            obj += labeledStrength * mstepObjective.labelLikelihood +
            	mstepObjective.parameterRegularizer;
        }

        System.out.println("After E-step of iteration::\t" + currIter);
        System.out.println("Entropy of Q::\t" + constraint.entropyObjective);
        System.out.println("Negative Unlabeled Likelihood::\t" +
        		constraint.likelihoodObjective);
        System.out.println("Graph Violation::\t" + constraint.graphObjective);
        System.out.println("*** Combined objective::\t" + obj);

        timer.stamp("estep-end");
        timer.printIntervalsInMins("estep-start", "estep-end");
        return obj;
    }

		
	public void trainModel()
	{
		initializeModel();
		
		boolean success = false;
		double prevObjective = Double.POSITIVE_INFINITY;
		double prevTime = 1e-3 * System.currentTimeMillis();
		
		GraphPropagationTester.runPropagation(corpus, graph);
		System.out.println("*** Graph prop acc:\t" + GraphPropagationTester.totalAcc);
		transductive = true;
		doAnalysis = false;
		Arrays.fill(softEmpiricalCounts, 0);
	
		for(int sid = 0; sid < corpus.numInstances; sid++) {
			AbstractSequence instance = corpus.getInstance(sid);
			if(instance.isLabeled) continue;
			for(int i = 0; i < instance.length; i++) {
				int nid = instance.nodes[i];
				if(nid >= 0) hardDecoding[sid][i] = GraphPropagationTester.pred[nid];
				else {
					hardDecoding[sid][i] = corpus.numTags - 1; // special case of punctuation
				}
			}
			model.addToEmpirical(sid, hardDecoding[sid], softEmpiricalCounts);
		}
	
		for(currIter = 0; currIter < config.numEMIters && !success; currIter++) {
			System.out.println("Iteration:: " + currIter);
		
			double currObjective = corpusMStep();
			double currTime = 1e-3 * System.currentTimeMillis();
					
			double objDec = prevObjective - currObjective;
			if(currIter > 2 && objDec < config.emStopThreshold) 
				success = true;
			
			System.out.println("obj change::\t" + objDec + "\ttime elapsed::\t" + (currTime - prevTime) + "\t(sec)");
			
			prevObjective = currObjective;
			prevTime = currTime;
		
			if(currIter == 0) {
				System.out.print("*** CRF Baseline:\t");
			}
			System.out.print("*** Training:\t");
			trainAcc = testModel(corpus.trains);
			System.out.print("*** Testing:\t");
			testAcc = testModel(corpus.tests);
		}

		System.out.println("EM Success:\t" + success);
	}
	
	public double testModel(int[] instanceIDs) {
		SentenceMonitorThread[] threads =
				new SentenceMonitorThread[config.numThreads];
		double tokenAccuracy = .0, sequenceAccuracy = .0, tokenNorm = .0;
		
		try {
			for(int i = 0; i < threads.length; i++) {
				threads[i] = new SentenceMonitorThread(i, threads.length,
						instanceIDs);
				threads[i].start();
			}
			
			for(int i = 0; i < threads.length; i++) threads[i].join();
			for(int i = 0; i < threads.length; i++) {
				tokenAccuracy += threads[i].numCorrectTokens;
				sequenceAccuracy += threads[i].numCorrectSequences;
				tokenNorm += threads[i].numTokens;
			}
			
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		tokenAccuracy /= tokenNorm;
		System.out.print(String.format("token acc.\t%.3f%%", 100.0 * tokenAccuracy));
		System.out.println(String.format("\tsequence acc.\t%.3f%%",
				100.0 * sequenceAccuracy / instanceIDs.length));
		
		return tokenAccuracy;
	}
	
	private double twoNormSquared(double[] x) {
		double norm = .0;
		for(double v : x) norm += v * v;
		return norm;
	}

	public double testAndAnalyze(int[] instanceIDs, String outputFileLabel) {
		SentenceMonitorThread[] threads = new SentenceMonitorThread[config.numThreads];
		double tokenAccuracy = .0, sequenceAccuracy = .0, tokenNorm = .0;
		doAnalysis = true;
		try {
			for(int i = 0; i < threads.length; i++) {
				threads[i] = new SentenceMonitorThread(i, threads.length, instanceIDs);
				threads[i].start();
			}
			
			for(int i = 0; i < threads.length; i++) threads[i].join();
			for(int i = 0; i < threads.length; i++) {
				tokenAccuracy += threads[i].numCorrectTokens;
				sequenceAccuracy += threads[i].numCorrectSequences;
				tokenNorm += threads[i].numTokens;
			}	
			
			tokenAccuracy /= tokenNorm;
			System.out.print(String.format("token acc.\t%.3f%%",
					100.0 * tokenAccuracy));
			System.out.println(String.format("\tsequence acc.\t%.3f%%",
					100.0 * sequenceAccuracy / instanceIDs.length));	
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		doAnalysis = false;
		
		return tokenAccuracy;
	}
	
	public class MStepObjective extends Objective 
	{
		protected double objective, labelLikelihood, softLikelihood, parameterRegularizer;
		int nrFeatures;
		boolean transductive;
		SentenceUpdateThread[] threads;
		int numThetaUpdates;
	
		public MStepObjective(double[] parameters, boolean transductive) 
		{
			this.nrFeatures = parameters.length;
			this.transductive = transductive;
			this.gradient = new double[nrFeatures];		
			this.parameters = parameters;
			
			this.threads = new SentenceUpdateThread[config.numThreads];
			this.numThetaUpdates = 0;
			setParameters(parameters);
			
		}
		
		public void updateObjectiveAndGradient()
		{
			double gpSquared = config.gaussianPrior * config.gaussianPrior;
	
			for (int i = 0; i < gradient.length; i++) {
				gradient[i] = parameters[i] / gpSquared - labeledStrength * empiricalCounts[i];
				//if(transductive) 
				gradient[i] -= softEmpiricalCounts[i];
			}
		
			parameterRegularizer = twoNormSquared(parameters) / (2.0 * gpSquared);
			objective = parameterRegularizer; 	
			labelLikelihood = 0;
			softLikelihood = 0;
			
			try {
				for(int i = 0; i < config.numThreads; i++) {
					threads[i] = new SentenceUpdateThread(i, threads.length, corpus.numInstances);
					threads[i].start();
				}
				
				for(int i = 0; i < config.numThreads; i++)	threads[i].join();
				
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			for(int i = 0; i < config.numThreads; i++) {
				for(int j = 0; j < gradient.length; j++)
					gradient[j] += threads[i].localGradient[j];
				
				labelLikelihood += threads[i].localLabelLikelihood;
				softLikelihood += threads[i].localSoftLikelihood;
				objective += labeledStrength * threads[i].localLabelLikelihood + 
								threads[i].localSoftLikelihood;
				
				threads[i] = null;
			}

			if(updateCalls % 10 == 0) {
				System.out.println("iteration:: " + updateCalls);
				System.out.println("objective:: " + objective + "\tlabeled:: " +
						labelLikelihood + "\tunlabeled:: " + softLikelihood);
			}
		}
		
		@Override
		public double getValue() {
			functionCalls++;
			return objective;
		}
		
		@Override
		public double[] getGradient(){
			gradientCalls++;
			return gradient;
		}		

		@Override
		public void setParameters(double[] newParameters) {
			super.setParameters(newParameters);
			updateObjectiveAndGradient();
		}

		@Override
		public String toString() {
			return "ocr discriminative model objective";
		}
		
		private class SentenceUpdateThread extends Thread 
		{
			@SuppressWarnings("unused")
			private final int threadID, startJobID, endJobID;
			private double[] localGradient;
			private double localLabelLikelihood, localSoftLikelihood;
			private SecondOrderFactorGraph model;
			
			public SentenceUpdateThread(int myID, int numThreads, int numJobs) {
				threadID = myID;
				int batchSize = numJobs / numThreads;
				startJobID = myID * batchSize;
				endJobID = (myID < numThreads - 1 ? startJobID + batchSize : numJobs);
				
				localGradient = new double[theta.length];
				model = new SecondOrderFactorGraph(corpus,	potentialFunction, fiter);
			}
			
			public void run()
			{
				localLabelLikelihood = 0;
				localSoftLikelihood = 0;
				Arrays.fill(localGradient, 0.0);
				
				for(int sid = startJobID; sid < endJobID; sid ++) {
					AbstractSequence instance = corpus.getInstance(sid);
					int length = instance.length;
			
					if (instance.isLabeled) {
						model.computeScores(instance, parameters, config.backoff);
						model.computeMarginals();	
						model.addToExpectation(sid, localGradient, labeledStrength);
						localLabelLikelihood += model.logNorm;
						
						for (int i = 0; i <= length; i++) {
							int s = (i == length ? corpus.finalState : instance.tags[i]);
							int sp = (i == 0 ? corpus.initialState : instance.tags[i-1]);
							int spp = (i == 0 ? corpus.initialStateSO : (i == 1 ? corpus.initialState : instance.tags[i-2]));
							localLabelLikelihood -= model.edgeScore[i][s][sp][spp] + model.nodeScore[i][s];
						}						
					}
					else {
						model.computeScores(instance, parameters, config.backoff);
						model.computeMarginals();	
						model.addToExpectation(sid, localGradient, 1.0);
						localSoftLikelihood += model.logNorm;
					
						int[] decoded = hardDecoding[sid];
						for(int i = 0; i <= length; i++) {
							int s = (i == length ? corpus.finalState : decoded[i]);
							int sp = (i == 0 ? corpus.initialState : decoded[i-1]);
							int spp = (i == 0 ? corpus.initialStateSO : (i == 1 ? corpus.initialState : decoded[i-2]));
							localSoftLikelihood -= model.edgeScore[i][s][sp][spp] + model.nodeScore[i][s];
						}
					}	
				}
			}
		}

	}
	
	synchronized void printPredictedIntance(AbstractCorpus corpus,
			AbstractSequence instance, int[] prediction) {
		for(int i = 0; i < instance.length; i++)
			System.out.print(corpus.getPrintableWord(instance.tokens[i]) + "\t");
		System.out.println();
		
		for(int i = 0; i < instance.length; i++)
			System.out.print(corpus.getPrintableTag(instance.tags[i]) + "\t");
		System.out.println();
		
		for(int i = 0; i < instance.length; i++)
			System.out.print(corpus.getPrintableTag(prediction[i]) + "\t");
		
		System.out.println("\n");
	}
	
	private class SentenceMonitorThread extends Thread 
	{
		@SuppressWarnings("unused")
		private final int threadID, startJobID, endJobID;
		private final int[] jobs;
		private SecondOrderFactorGraph model;
		public double numCorrectTokens, numCorrectSequences, numTokens;
		
		public SentenceMonitorThread(int threadID, int numThreads, int[] jobs)
		{
			this.threadID = threadID;
			this.jobs = jobs;
			int batchSize = jobs.length / numThreads;
			startJobID = threadID * batchSize;
			endJobID = (threadID < numThreads - 1 ? startJobID + batchSize : jobs.length);
			model = new SecondOrderFactorGraph(corpus,	potentialFunction, fiter);
			
		}
		
		public void run()
		{
			numCorrectTokens = 0;
			numCorrectSequences = 0;
			numTokens = 0;
			
			for(int sid = startJobID; sid < endJobID; sid ++) {
				AbstractSequence instance = corpus.getInstance(jobs[sid]);
				model.computeScores(instance, theta, config.backoff);
				model.computeMarginals();
				
				double acc = model.decodeAndEvaluate(instance.tags);
				numTokens += instance.length;
				numCorrectTokens += acc;
				numCorrectSequences += (acc == instance.length ? 1 : 0);
			}
		}
		
	}


}
