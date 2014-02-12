package data;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;
import gnu.trove.TIntArrayList;

public class AbstractCorpus {
	public int[] trains, tests;
	public int maxSequenceID, maxSequenceLength;
	public int numStates, numTags, initialState, finalState, initialStateSO;
	public int numInstances, numNodes, numWords;
	
	public int[] nodeFrequency;
	
	public AbstractSequence getInstance(int id) {
		return null;
	}
	
	public void printInstance(int sid, PrintStream ostr) {
		//FIXME: to implement
	}
	
	public void sampleFromFolder(int numLabels, int seedFolder, Random sampler) {
		sampleFromFolderUnsorted(numLabels, seedFolder, sampler);
		Arrays.sort(trains);
		Arrays.sort(tests);
	}
	
	public void sampleFromFolderUnsorted(int numLabels, int seedFolder,
			Random sampler) {
		TIntArrayList poolIds = new TIntArrayList();
		TIntArrayList trainIds = new TIntArrayList();
		TIntArrayList testIds = new TIntArrayList();

		for(int i = 0; i < numInstances; i++) {
			int fid = getInstance(i).foldID; 
			if(fid == seedFolder) {
				poolIds.add(i);
			}
		}
		
		int[] pool = poolIds.toNativeArray();
		boolean[] sampled = new boolean[pool.length];
		for(int i = 0; i < pool.length; i++) { 
			sampled[i] = false;
		}
		
		int pidx;
		while(trainIds.size() < numLabels) {
			while(sampled[pidx = sampler.nextInt(pool.length)]) ;
			trainIds.add(pool[pidx]);
			sampled[pidx] = true;
			getInstance(pool[pidx]).isLabeled = true;
		}
		
		for(int i = 0; i < numInstances; i++) {
			if(!getInstance(i).isLabeled) {
				testIds.add(i);
			}
		}
		
		trains = trainIds.toNativeArray();
		tests = testIds.toNativeArray();	
		System.out.println("Number of trains:\t" + trains.length +
				"\ttests:\t" + tests.length);
	}
	
	public void resetLabels(int[] newTrains, int[] newTests)	{
		trains = new int[newTrains.length];
		tests = new int[newTests.length];
		int nrTrains = 0, nrTests = 0;
		
		for(int sid : newTrains) {
			AbstractSequence instance = getInstance(sid);
			instance.isLabeled = true;
			trains[nrTrains++] = sid;
		}
		
		for(int sid : newTests) {
			AbstractSequence instance = getInstance(sid);
			instance.isLabeled = false;
			tests[nrTests++] = sid;
		}
		
		Arrays.sort(trains);
		Arrays.sort(tests);
		System.out.println(String.format("Reset labels with: trains (%d)," +
				" tests (%d)", nrTrains, nrTests));
	}

	public void printCrossValidationInfo() {
		System.out.print(String.format("\ntrains (%d):\t", trains.length));
		for(int i : trains) { 	
			System.out.print(i + " " );
		}
		System.out.print(String.format("\ntests: (%d)\t",  tests.length));
		System.out.println();
	}
	
	public String getTag(int tid) {
		return "";
	}
	
	public String getPrintableWord(int wordID) {
		return "";
	}
	
	public String getPrintableTag(int tagID) {
		return "";
	}
}
