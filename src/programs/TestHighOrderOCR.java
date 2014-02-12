package programs;

import features.OCRSOPotentialFunction;
import java.io.IOException;
import models.AbstractFactorIterator;
import models.UnconstrainedFactorIterator;
import config.OCRConfig;
import trainers.SecondOrderEMTrainer;
import util.MemoryTracker;
import data.OCRCorpus;
import data.SparseSimilarityGraph;

public class TestHighOrderOCR {
	public static void main(String[] args)
			throws NumberFormatException, IOException {
		OCRConfig config = new OCRConfig(args);
		config.print(System.out);			

		MemoryTracker mem  = new MemoryTracker();
		mem.start(); 
		
		OCRCorpus corpus = new OCRCorpus(config.dataPath, Integer.MAX_VALUE);
		RandomSampingHelper.resampleTrains(config, corpus);
		
		SparseSimilarityGraph graph = null;
		try {
			graph = new SparseSimilarityGraph(config.graphPath, corpus.numNodes);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		OCRSOPotentialFunction potentialFunction = new OCRSOPotentialFunction(
				corpus, config);
		AbstractFactorIterator fiter = new UnconstrainedFactorIterator(corpus);
		SecondOrderEMTrainer trainer = new SecondOrderEMTrainer(corpus,
				potentialFunction, graph, fiter, config);
	
		trainer.trainModel();
		
		System.out.print("Training accuracy::\t");
		trainer.testModel(corpus.trains);
		System.out.print("Testing accuracy::\t");
		trainer.testModel(corpus.tests);
		
		mem.finish();
		System.out.println("Memory usage:: " + mem.print());
	}

}
