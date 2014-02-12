package graph;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

public class KNNGraphConstructer {
	CountDictionary ngramCounts;
	int[][] features;
	double[][] featureVals;
	int numNodes;
	int numNeighbors;
	boolean mutualKNN;
	EdgeList[] edges;
	String graphPath, ngramPath;
	int numThreads;
	double similarityThreshold;
	
	public KNNGraphConstructer(CountDictionary ngramCounts, int[][] features,
			double[][] featureVals, int numNeighbors, boolean mutualKNN,
			double similarityThreshold, String graphPath, String ngramPath,
			int numThreads) {
		this.ngramCounts = ngramCounts;
		this.features = features;
		this.featureVals = featureVals;		
		this.numNodes = features.length;
		this.numNeighbors = numNeighbors;
		this.mutualKNN = mutualKNN;
		this.similarityThreshold = similarityThreshold;
		this.numThreads = numThreads;
		
		this.graphPath = graphPath;
		this.ngramPath = ngramPath;
	}
	
	public void run() throws UnsupportedEncodingException,
		FileNotFoundException, IOException {
		System.out.println(String.format("Starting to build graph with " +
				"%d nodes, and %d neighbors with %s KNN method. ",
				numNodes, numNeighbors, (mutualKNN ? "mutual" : "symmetric")));
		
		edges = new EdgeList[numNodes];
		for(int i = 0; i < numNodes; i++) {
			edges[i] = new EdgeList(numNeighbors);
		}
		
		int batchSize = numNodes / numThreads;
		EdgeBuilderThread[] threads = new EdgeBuilderThread[numThreads];
		for (int i = 0; i < numThreads; i++) {
			int start = i * batchSize;
			int end = (i == numThreads - 1 ? numNodes : start + batchSize);
			threads[i] = new EdgeBuilderThread(i, start, end);
			threads[i].start();
		}
		for (int i = 0; i < numThreads; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) { }
		}
		symmetrifyAndSaveGraph();
	}

	protected synchronized void print(String string) {
		System.out.print(string);
	}

	protected class EdgeBuilderThread extends Thread {
		int start, end, id;
		boolean finished;

		EdgeBuilderThread(int id, int start, int end) {
			this.id = id;
			this.start = start;
			this.end = end;
			this.finished = false;
			print("Thread " + id + " processing nodes " + start + " to "
					+ end + "\n");
		}

		@Override
		public void run() {
			long startTime = System.currentTimeMillis();
			for (int i = start; i < end; i++) {
				for (int j = 0; j < numNodes; j++) {
					if (i != j) {
						double sim = cosineSimilarity(features[i],
								featureVals[i], features[j], featureVals[j]);
						if (sim >= similarityThreshold) {
							edges[i].add(new Edge(j, sim));
						}
					}
				}
		
				if (i > start && (i - start) % 1000 == 0) {
					int offset = (i - start);
					long timeElapsedMin = 1 + (System.currentTimeMillis()
							- startTime) / 60000;
					long nodesPerMin = offset / timeElapsedMin;
					print("\nThread" + id + "\t::" + offset
							+ " nodes finished, " + (end -start - offset)
							+ " nodes to go. " + nodesPerMin
							+ " nodes per minute\n");
				}
			}
		}

		private double cosineSimilarity(int[] k0, double[] v0, int[] k1,
				double[] v1) {
			if(k0.length == 0 || k1.length == 0 || k0[0] > k1[k1.length-1] ||
					k1[0] > k0[k0.length-1]) { 
				return 0.0;
			}
			double sim = 0;
			for (int i = 0, j = 0; i < k0.length && j < k1.length; ) {
				if (k0[i] == k1[j]) {
					sim += v0[i] * v1[j];
					i ++; j ++;
				}
				else if (k0[i] < k1[j]) {
					i ++;
				}
				else {
					j ++;
				}
			}
			return sim;
		}
	}
	
	public void symmetrifyAndSaveGraph()
			throws UnsupportedEncodingException, FileNotFoundException,
			IOException  {
		// Symmetrifying Graph
		for (int i = 0; i < numNodes; i++) {
			edges[i].freeze();
		}
		
		int nrEdges = 0, nrEmptyNodes = 0;
		if (!mutualKNN) {
			for (int i = 0; i < numNodes; i++) {
				for (Iterator<Edge> itr = edges[i].iterator(); itr.hasNext(); ) {
					Edge e = itr.next();
					edges[e.neighbor].symAdd(new Edge(i, e.weight));
				}
			}
		}
		
		double weightNorm = 0;
		double avgDegree = 0, maxDegree = -1, minDegree = Double.MAX_VALUE;
		double avgFreqEmpty = 0, avgFreqNonEmpty = 0; 
		System.out.println("Saving graph to file: " + graphPath);
		BufferedWriter fout = new BufferedWriter(new FileWriter(graphPath));
		
		for (int i = 0; i < numNodes; i++) {
			int degree = 0;
			for (Iterator<Edge> itr = edges[i].iterator(); itr.hasNext(); ) {
				Edge e = itr.next();
				if (!edges[e.neighbor].contains(i) || e.weight <= 0) {
					continue;
				}
				++ degree;
				++ nrEdges;
				weightNorm += e.weight;
	
				fout.write(String.format("%d\t%d\t%.12f\n", i + 1,
						e.neighbor + 1, e.weight));
			}
			
			if (degree == 0) {
				++ nrEmptyNodes;
			}
			avgDegree += degree;
			maxDegree = Math.max(maxDegree, degree);
			minDegree = Math.min(minDegree, degree);
		}
		fout.close();
		
		System.out.println("Saving ngram index to file: " + ngramPath);
		fout = new BufferedWriter(new FileWriter(ngramPath));
		
		for (int nid= 0; nid < numNodes; nid++ ) {
			String ngram = ngramCounts.index2str.get(nid);
			fout.write(String.format("%d\t%s\n", nid + 1, ngram));
		}
		fout.close();
		
		System.out.println("graph weight norm: " + weightNorm +
				"\t num edges:\t" + nrEdges);
		
		System.out.println("Averaged node degree: " + avgDegree / numNodes + 
				"\n Maximum node degree: " + maxDegree + 
				"\n Minimum node degree: " + minDegree + 
				"\n Number of nodes: " + numNodes + 
				String.format("\n Number of empty nodes: %d (%.2f%%)", 
						nrEmptyNodes, 100.0 * nrEmptyNodes / numNodes));

		System.out.println(String.format(
				"Averaged empty node frequency: %f\n" + 
				"Averaged non-empty node frequency: %f\n",
				avgFreqEmpty / nrEmptyNodes, 
				avgFreqNonEmpty / (numNodes - nrEmptyNodes)));
	}	
}
