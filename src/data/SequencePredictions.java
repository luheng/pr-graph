package data;

import gnu.trove.TIntArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class SequencePredictions {
	
	AbstractCorpus corpus;
	public int[] sids;
	public int[][] pred;
	public double[] sacc; // sequence accuracy
	double accuracy, norm;
	
	public SequencePredictions(AbstractCorpus corpus, String predFilePath) throws IOException
	{
		this.corpus = corpus;
		TIntArrayList sidList = new TIntArrayList();
		pred = new int[corpus.numInstances][];
		sacc = new double[corpus.numInstances];
		
		String currLine;
		BufferedReader reader = new BufferedReader(new FileReader(predFilePath));
	
		while ((currLine = reader.readLine()) != null) {
			String[] info = currLine.trim().split("\t");
			int sid = Integer.parseInt(info[0]);
			int len = info.length - 1;
			sidList.add(sid);
			pred[sid] = new int[len];
			for(int i = 0; i < len; i++)
				pred[sid][i] = Integer.parseInt(info[i+1]);
		}
		reader.close();
		sids = sidList.toNativeArray();
			
		// evaluate accuracy
		accuracy = norm = 0;
		for(int sid : sids) {
			sacc[sid] = .0;
			
			int[] tags = corpus.getInstance(sid).tags;
			for(int i = 0; i < tags.length; i++)
				if(tags[i] == pred[sid][i]) {
					++ accuracy;
					++ sacc[sid];
				}
			norm += pred[sid].length;
			sacc[sid] /= pred[sid].length;
		}
		
		accuracy /= norm;
		System.out.println("Finished reading prediction from " + predFilePath + " ...\tacc::\t" + accuracy);
	}
	
	public void print(int sid)
	{
		System.out.println("sequence id:\t" + sid + "\tacc:\t" + sacc[sid]);
		int[] tags = corpus.getInstance(sid).tags;
		for(int j = 0; j < tags.length; j++)
			System.out.print(corpus.getTag(tags[j]));
		System.out.println();
		for(int j = 0; j < pred[sid].length; j++)
			System.out.print(corpus.getTag(pred[sid][j]));
		System.out.println();
	}
	
}
