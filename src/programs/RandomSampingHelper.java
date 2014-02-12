package programs;

import java.util.Random;

import config.Config;
import data.AbstractCorpus;

public class RandomSampingHelper {
	public static void resampleTrains(Config config, AbstractCorpus corpus) {
		int fsize = config.numLabels;
		corpus.sampleFromFolderUnsorted(fsize * config.numSampleFolds,
				config.seedFolder, new Random(config.randomSeed));
		
		int[] newTests = new int[corpus.numInstances - fsize];
		int[] newTrains = new int[fsize];
		
		int dsize = 0;
		for (int i = 0; i < corpus.tests.length; i++) {
			newTests[dsize++] = corpus.tests[i];
		}
		
		for (int k = 0; k < config.numSampleFolds; k++) {
			for (int i = 0; i < fsize; i++) {
				int tid = k * fsize + i;
				if (k == config.sampleFoldID) {
					newTrains[i] = corpus.trains[tid];
				}
				else { 
					newTests[dsize++] = corpus.trains[tid];
				}
			}
		}
		corpus.resetLabels(newTrains, newTests);
		corpus.printCrossValidationInfo();
	}
}
