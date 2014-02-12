package programs;

import features.PosSOPotentialFunction;
import java.io.IOException;
import models.AbstractFactorIterator;
import models.PrunedTagIterator;
import config.PosConfig;
import trainers.SecondOrderEMTrainer;
import util.MemoryTracker;
import data.NGramMapper;
import data.PosCorpus;
import data.SparseSimilarityGraph;

public class TestHighOrderPos {
	public static void main(String[] args)
			throws NumberFormatException, IOException {
		PosConfig config = new PosConfig(args);
		config.print(System.out);			

		MemoryTracker mem  = new MemoryTracker();
		mem.start(); 
		
		String[] dataFiles = config.dataPath.split(",");
		
		NGramMapper ngmap = new NGramMapper(config);
		PosCorpus corpus = new PosCorpus(dataFiles, ngmap, config);
		RandomSampingHelper.resampleTrains(config, corpus);

		SparseSimilarityGraph graph = null;
		try {
			graph = new SparseSimilarityGraph(config.graphPath, corpus.numNodes);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		PosSOPotentialFunction potentialFunction = new PosSOPotentialFunction(
				corpus, config);
		AbstractFactorIterator fiter = new PrunedTagIterator(corpus);
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
