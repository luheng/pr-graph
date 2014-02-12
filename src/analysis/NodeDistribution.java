package analysis;

import java.util.Arrays;

import constraints.LatticeHelper;
import data.AbstractCorpus;
import data.AbstractSequence;

public class NodeDistribution {
	public double[][] dist;
	public int[] freq;
	
	public NodeDistribution(int numNodes, int numLabels) {
		dist = new double[numNodes][numLabels];
		freq = new int[numNodes];
	}
	
	public void initialize(AbstractCorpus corpus, int[] sids) {
		LatticeHelper.deepFill(dist, 0.0);
		Arrays.fill(freq, 0);
		
		for (int sid : sids) {
			AbstractSequence instance = corpus.getInstance(sid);
			for (int i = 0; i < instance.length; i++) {
				int nid = instance.nodes[i];
				int tid = instance.tags[i];
				if (nid >= 0) {
					dist[nid][tid] ++;
					freq[nid] ++;
				}
			}
		}
		for (int i = 0; i < dist.length; i++) {
			for (int j = 0; j < dist[i].length; j++) {
				dist[i][j] /= freq[i];
			}
		}
	}
}