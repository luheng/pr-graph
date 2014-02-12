package config;

import java.io.PrintStream;
import org.kohsuke.args4j.Option;

public class Config {
	@Option(name = "-data-path", usage="")
	public String dataPath = "./data/letter.data";
	
	@Option(name = "-output-path", usage="")
	public String outputPath = "test.out";
	
	@Option(name = "-graph-path", usage="")
	public String graphPath = "./data/graph/es-60nn-dep.grph";
	
	@Option(name = "-num-labels", usage="")
	public int numLabels = 100;
	
	@Option(name = "-label-strength", usage="")
	public double labelStrength = 1.0;
	
	@Option(name = "-graph-strength", usage="")
	public double graphRegularizationStrength = 1.0; 
	
	@Option(name = "-gaussian-prior", usage="")
	public double gaussianPrior = 100.0;
	
	@Option(name = "-backoff", usage="")
	public double backoff = 1e-8;
	
	@Option(name = "-estep-backoff", usage="")
	public double estepBackoff = 1e-8;
	
	@Option(name = "-num-em-iters", usage="")
	public int numEMIters = 1;
	
	@Option(name = "-num-estep-iters", usage="")
	public int numEstepIters = 50;
	
	@Option(name = "-num-mstep-iters", usage="")
	public int numMstepIters = 50;
	
	@Option(name = "-num-threads", usage="")
	public int numThreads= 4;
	
	@Option(name = "-em-stop", usage="")
	public double emStopThreshold = 0.01;
	
	@Option(name = "-estep-stop", usage="")
	public double estepStopThreshold = 0.01;
	
	@Option(name = "-mstep-stop", usage="")
	public double mstepStopThreshold = 1e-5;
	
	@Option(name = "-eta", usage="")
	public double initialLearningRate = 0.2;

	@Option(name = "-estep-init", usage="")
	public double estepInit = 1.0;

	@Option(name = "-seed-folder", usage="")
	public int seedFolder = 0;
	
	@Option(name = "-scale-features", usage="")
	public boolean scaleFeatures;
	
	@Option(name = "-num-sample-folds", usage="")
	public int numSampleFolds = 10;
	
	@Option(name = "-sample-fold", usage="")
	public int sampleFoldID = 0; 
	
	@Option(name = "-random-seed", usage="")
	public int randomSeed = 12345;
	
	public Config() {
	}
	
	public void print(PrintStream ostr)	{
		ostr.println("-data-path\t" + dataPath);
		ostr.println("-graph-path\t" + graphPath);
		ostr.println("-output-path\t" + outputPath);
		ostr.println("-num-threads\t" + numThreads);
		ostr.println("-num-labels\t" + numLabels);
		ostr.println("-label-strength\t" + labelStrength);
		ostr.println("-graph-strength\t" + graphRegularizationStrength);
		ostr.println("-gaussian-prior\t" + gaussianPrior);
		ostr.println("-backoff\t" + backoff);
		ostr.println("-estep-backoff\t" + estepBackoff);
		ostr.println("-num-em-iters\t" + numEMIters);
		ostr.println("-num-estep-iters\t" + numEstepIters);
		ostr.println("-num-mstep-iters\t" + numMstepIters);
		ostr.println("-em-stop-threshold\t" + emStopThreshold);
		ostr.println("-estep-stop-threshold\t" + estepStopThreshold);
		ostr.println("-mstep-stop-threshold\t" + mstepStopThreshold);
		ostr.println("-eta0\t" + initialLearningRate);
		ostr.println("-estep-uniform-init\t" + estepInit);
		ostr.println("-seed-folder\t" + seedFolder);
		ostr.println("-scale-features\t" + scaleFeatures);
		ostr.println("-num-cv-folds\t" + numSampleFolds);
		ostr.println("-sample-fold-id\t" + sampleFoldID);
		ostr.println("-random-seed\t" + randomSeed);
	}
}
