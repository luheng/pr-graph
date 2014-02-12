package data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import gnu.trove.TDoubleArrayList;
import gnu.trove.TIntArrayList;

public class SparseSimilarityGraph {
	public int numNodes; 
	public TIntArrayList[] edges;
	public TDoubleArrayList[] weights;
	public double[] degrees;
	public double weightNorm;
	
	public SparseSimilarityGraph(String graphPath, int numNodes)
			throws NumberFormatException, IOException {
		this.numNodes = numNodes;
		this.edges = new TIntArrayList[numNodes];
		this.weights = new TDoubleArrayList[numNodes];
		this.degrees = new double[numNodes];
		this.weightNorm = 0;
		
		for (int i = 0; i < numNodes; i++) {
			edges[i] = new TIntArrayList();
			weights[i] = new TDoubleArrayList();
			degrees[i] = 0.0;
		}
		
		String currLine;
		BufferedReader reader = new BufferedReader(new FileReader(graphPath));

		System.out.println("Reading graph");		
		while ((currLine = reader.readLine()) != null) {
			String[] info = currLine.trim().split("\t");
			if (info.length < 3) {
				continue;
			}
			int nid = Integer.parseInt(info[0]) - 1;
			int eid = Integer.parseInt(info[1]) - 1;
			double w = Double.parseDouble(info[2]);
			edges[nid].add(eid);
			weights[nid].add(w);
			degrees[nid] += w;
			weightNorm += w;
		}
		reader.close();
		System.out.println("Graph weight norm:\t" + weightNorm);
	}
	
	public double computeGoldViolation(AbstractCorpus corpus) {
		double violation = 0;
		double[][] goldDist = new double[corpus.numNodes][corpus.numStates - 2];
		
		for (int i = 0; i < corpus.numNodes; i++)
			for (int j = 0; j < corpus.numStates - 2; j++) {
				goldDist[i][j] = 0.0;
			}
		for (int i = 0; i < corpus.numInstances; i++) {
			AbstractSequence seq = corpus.getInstance(i);
			for (int j = 0; j < seq.length; j++) {
				if (seq.nodes[j] >= 0) {
					goldDist[seq.nodes[j]][seq.tags[j]] +=
						1.0 / corpus.nodeFrequency[seq.nodes[j]];
				}
			}
		}		
		for (int i = 0; i < corpus.numNodes; i++) {		
			for (int j = 0; j < edges[i].size(); j++) {
				int e = edges[i].get(j);
				double w = weights[i].get(j);
				for (int k = 0; k < corpus.numStates - 2; k++) {
					double diff = goldDist[i][k] - goldDist[e][k];
					violation += diff * diff * w / 2;
				}
			}
		}
		return violation;
	}
}
