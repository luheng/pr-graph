package trainers;

import java.util.Arrays;
import analysis.GeneralTimer;
import models.AbstractFactorIterator;
import models.SecondOrderFactorGraph;
import config.Config;
import constraints.SecondOrderTypeEG;
import optimization.gradientBasedMethods.LBFGS;
import optimization.gradientBasedMethods.Objective;
import optimization.gradientBasedMethods.Optimizer;
import optimization.gradientBasedMethods.stats.OptimizerStats;
import optimization.linesearch.InterpolationPickFirstStep;
import optimization.linesearch.LineSearchMethod;
import optimization.linesearch.WolfRuleLineSearch;
import optimization.stopCriteria.CompositeStopingCriteria;
import optimization.stopCriteria.NormalizedValueDifference;
import data.AbstractCorpus;
import data.AbstractSequence;
import data.SparseSimilarityGraph;
import features.SecondOrderPotentialFunction;

public class SecondOrderEMTrainer {
	AbstractCorpus corpus;
	SecondOrderPotentialFunction potentialFunction;
	AbstractFactorIterator fiter;
	SecondOrderFactorGraph model;
	SparseSimilarityGraph graph;
	SecondOrderTypeEG constraint;
	MStepObjective mstepObjective;
	
	int numParameters;
	double[] theta;
	double[] empiricalCounts, expectedCounts, softEmpiricalCounts;

	Config config;
	boolean transductive;
	int currIter;
	public double trainAcc, devAcc, testAcc;
	
	// LBFG-S stuff
	LineSearchMethod lineSearch;
	CompositeStopingCriteria stopping;
	Optimizer optimizer;
	OptimizerStats stats;
	double prevStepSize;

	GeneralTimer timer;
	
	public SecondOrderEMTrainer(AbstractCorpus corpus,
			SecondOrderPotentialFunction potentialFunction, 
			SparseSimilarityGraph graph, AbstractFactorIterator fiter,
			Config config) {
		this.corpus = corpus;
		this.potentialFunction = potentialFunction;
		this.fiter = fiter;
		this.graph = graph;
		this.config = config;
	}
	
	private void initializeModel() {
		numParameters = potentialFunction.getNumFeatures();
		theta = new double[numParameters];
		empiricalCounts = new double[numParameters];
		expectedCounts = new double[numParameters];
		softEmpiricalCounts = new double[numParameters];
		prevStepSize = 1.0;
		
		Arrays.fill(empiricalCounts, 0.0);
		model = new SecondOrderFactorGraph(corpus, potentialFunction, fiter);
		constraint = new SecondOrderTypeEG(corpus, graph, potentialFunction,
				fiter, config);
		
		for (int tid = 0; tid < corpus.numInstances; tid++) {
			AbstractSequence instance = corpus.getInstance(tid);
			if (instance.isLabeled) {
				model.addToEmpirical(tid, instance.tags, empiricalCounts);
			}
		}
		timer = new GeneralTimer();
	}
	
	private double corpusMStep() {
		System.out.println("Corpus M Step");
		timer.stamp("mstep-start");
		
		lineSearch = new WolfRuleLineSearch(
				new InterpolationPickFirstStep(prevStepSize), 1e-4, 0.9, 10);
		lineSearch.setDebugLevel(0);
		stopping = new CompositeStopingCriteria();
		stopping.add(new NormalizedValueDifference(config.mstepStopThreshold));
		optimizer = new LBFGS(lineSearch, 10);
		optimizer.setMaxIterations(config.numMstepIters);
		stats = new OptimizerStats();
		mstepObjective = new MStepObjective(theta, transductive); 
		boolean succeed = optimizer.optimize(mstepObjective, stats, stopping);
		prevStepSize = optimizer.getCurrentStep();
		System.out.println("success:\t" + succeed + "\twith latest stepsize:\t"
				+ prevStepSize);
	
		double obj = mstepObjective.objective;
		if(constraint != null) {
			obj += constraint.lpStrength / 2 * constraint.graphObjective -
					constraint.entropyObjective;
		}
				
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
	
	private double corpusEStep() {
		System.out.println("Corpus E Step");
		timer.stamp("estep-start");
		try {
			constraint.project(theta, mstepObjective.softLikelihood);
		} catch (InterruptedException e) { }
		
		double obj = constraint.objective;
		if (mstepObjective != null) { 
			obj += config.labelStrength * mstepObjective.labelLikelihood +
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
	
	public void trainModel() {
		initializeModel();
		
		transductive = false;
		boolean success = false;
		double prevObjective = Double.POSITIVE_INFINITY;
		double prevTime = 1e-6 * System.currentTimeMillis();
		
		for (currIter = 0; currIter < config.numEMIters && !success;
				currIter++) {
			System.out.println("Iteration:: " + currIter);
			
			if (currIter > 0) {
				corpusEStep();		
				transductive = true;
			}
			
			double currObjective = corpusMStep();
			double currTime = 1e-6 * System.currentTimeMillis();
			double objDec = Math.abs(prevObjective - currObjective) /
					prevObjective;
		
			if (currIter > 2 && objDec < config.emStopThreshold) {
				success = true;
			}
			System.out.println(String.format(
					"obj change::\t%.6f\ttime elapsed::\t%.2f\t(min)",
					objDec, (currTime - prevTime)));
			
			prevObjective = currObjective;
			prevTime = currTime;
			System.out.print("*** Training:\t");
			trainAcc = testModel(corpus.trains);
			System.out.print("*** Testing:\t");
			testAcc = testModel(corpus.tests);

			if (currIter == 0) {
				System.out.print("*** CRF Baseline:\t");
				testAndAnalyze(corpus.tests, "crf-baseline");
			}
		}
		System.out.println("EM Success:\t" + success);
	}
	
	public double testModel(int[] instanceIDs) {
		SentenceMonitorThread[] threads =
				new SentenceMonitorThread[config.numThreads];
		double tokenAccuracy = .0, sequenceAccuracy = .0, tokenNorm = .0;

		try {
			for (int i = 0; i < threads.length; i++) {
				threads[i] = new SentenceMonitorThread(i, threads.length,
						instanceIDs);
				threads[i].start();
			}
			
			for (int i = 0; i < threads.length; i++) threads[i].join();
			for (int i = 0; i < threads.length; i++) {
				tokenAccuracy += threads[i].numCorrectTokens;
				sequenceAccuracy += threads[i].numCorrectSequences;
				tokenNorm += threads[i].numTokens;
			}	
		} catch (InterruptedException e) { }
		
		tokenAccuracy /= tokenNorm;
		System.out.print(String.format("token acc.\t%.3f%%",
				100.0 * tokenAccuracy));
		System.out.println(String.format("\tsequence acc.\t%.3f%%",
				100.0 * sequenceAccuracy / instanceIDs.length));
		
		return tokenAccuracy;
	}
	
	public double testAndAnalyze(int[] instanceIDs, String outputFileLabel) {
		SentenceMonitorThread[] threads =
				new SentenceMonitorThread[config.numThreads];
		double tokenAccuracy = .0, sequenceAccuracy = .0, tokenNorm = .0;

		try {
			for (int i = 0; i < threads.length; i++) {
				threads[i] = new SentenceMonitorThread(i, threads.length,
						instanceIDs);
				threads[i].start();
			}
			for (int i = 0; i < threads.length; i++) {
				threads[i].join();
			}
			for (int i = 0; i < threads.length; i++) {
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
		}
		return tokenAccuracy;
	}
	
	private double twoNormSquared(double[] x) {
		double norm = .0;
		for(double v : x) norm += v * v;
		return norm;
	}
	
	public class MStepObjective extends Objective {
		protected double objective, labelLikelihood, softLikelihood,
			parameterRegularizer;
		int nrFeatures;
		boolean transductive;
		SentenceUpdateThread[] threads;
		int numThetaUpdates;
		double gpSquared;
		double[] gradientInit;
	
		public MStepObjective(double[] parameters, boolean transductive) {
			this.nrFeatures = parameters.length;
			this.transductive = transductive;
			this.gradient = new double[nrFeatures];		
			this.parameters = parameters;
			this.gpSquared = config.gaussianPrior * config.gaussianPrior;
			this.gradientInit = new double[parameters.length];
			
			for (int i = 0; i < parameters.length; i++) {
				gradientInit[i] = - config.labelStrength * empiricalCounts[i];
				if(transductive) {
					gradientInit[i] -= constraint.softEmpiricalCounts[i];
				}
			}
			this.threads = new SentenceUpdateThread[config.numThreads];
			this.numThetaUpdates = 0;
			setParameters(parameters);
		}
		
		public void updateObjectiveAndGradient() { 
			parameterRegularizer = twoNormSquared(parameters) /
					(2.0 * gpSquared); 
			objective = 0; 	
			labelLikelihood = 0;
			softLikelihood = 0;
			
			for (int i = 0; i < gradient.length; i++) {
				gradient[i] = gradientInit[i] + parameters[i] / gpSquared;
				labelLikelihood -= parameters[i] * empiricalCounts[i];
				softLikelihood -= parameters[i] *
						constraint.softEmpiricalCounts[i];
			}
			
			try {
				for (int i = 0; i < config.numThreads; i++) {
					threads[i] = new SentenceUpdateThread(i, threads.length,
							corpus.numInstances);
					threads[i].start();
				}
				for (int i = 0; i < config.numThreads; i++) {
					threads[i].join();
				}
			
				for (int i = 0; i < config.numThreads; i++) {
					for (int j = 0; j < gradient.length; j++) { 
						gradient[j] += threads[i].localGradient[j];
					}
					labelLikelihood += threads[i].localLabelLikelihood;
					softLikelihood += threads[i].localSoftLikelihood;
					threads[i] = null;
				}
			} catch (InterruptedException e) {
			}
			
			objective = parameterRegularizer + config.labelStrength *
					labelLikelihood;
			if (transductive) { 
				objective += softLikelihood;
			}
			if (updateCalls % 100 == 0) {
				System.out.println("iteration:: " + updateCalls);
				System.out.println("objective:: " + objective + "\tlabeled:: " +
						labelLikelihood + "\tunlabeled:: " + softLikelihood);
				System.out.println("gradient norm:: " +
						twoNormSquared(gradient));
				System.out.println("parameter norm:: " +
						twoNormSquared(parameters));
			}
		}
		
		@Override
		public double getValue() {
			functionCalls++;
			return objective;
		}
		
		@Override
		public double[] getGradient() {
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
		
		private class SentenceUpdateThread extends Thread {
			@SuppressWarnings("unused")
			private final int threadID, startJobID, endJobID;	
			private double[] localGradient;
			private double localLabelLikelihood, localSoftLikelihood;
			private SecondOrderFactorGraph model;
			
			public SentenceUpdateThread(int myID, int numThreads, int numJobs) {
				threadID = myID;
				int batchSize = numJobs / numThreads;
				startJobID = myID * batchSize;
				endJobID = (myID < numThreads - 1 ?
						startJobID + batchSize : numJobs);

				localGradient = new double[theta.length];
				model = new SecondOrderFactorGraph(corpus,	potentialFunction,
						fiter);
			}
			
			public void run() {
				localLabelLikelihood = 0;
				localSoftLikelihood = 0;
				Arrays.fill(localGradient, 0.0);
				
				for (int sid = startJobID; sid < endJobID; sid ++) {
					AbstractSequence instance = corpus.getInstance(sid);
					if (!instance.isLabeled && !transductive) {
						continue;
					}
					model.computeScores(instance, parameters, 0.0);
					model.computeMarginals();
					if (instance.isLabeled) {
						model.addToExpectation(sid, localGradient,
								config.labelStrength);
						localLabelLikelihood += model.logNorm;			
					}
					else {	
						model.addToExpectation(sid, localGradient, 1.0);
						localSoftLikelihood += model.logNorm;
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
	
	private class SentenceMonitorThread extends Thread {
		@SuppressWarnings("unused")
		private final int threadID, startJobID, endJobID;
		private final int[] jobs;
		private SecondOrderFactorGraph model;
		public double numCorrectTokens, numCorrectSequences, numTokens;
		
		public SentenceMonitorThread(int threadID, int numThreads, int[] jobs) {
			this.threadID = threadID;
			this.jobs = jobs;
			int batchSize = jobs.length / numThreads;
			startJobID = threadID * batchSize;
			endJobID = (threadID < numThreads - 1 ?
					startJobID + batchSize :jobs.length);
			model = new SecondOrderFactorGraph(corpus,	potentialFunction,
					fiter);
		}
		
		public void run() {
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
