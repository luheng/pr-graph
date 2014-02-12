package constraints;

import models.AbstractFactorIterator;
import models.FirstOrderFactorGraph;
import util.ArrayMath;
import data.AbstractSequence;

public class FirstOrderEGDMonitor {
	public int numCorrect, numTotal;
	public double avgent, maxmaxq, minmaxq, avgmaxq, avgstd;
	public double[] esrange, nsrange;
	int numStates;
	AbstractFactorIterator fiter;
	
	public FirstOrderEGDMonitor(int numStates, AbstractFactorIterator fiter) {
		numCorrect = 0;
		numTotal = 0;
		avgent = avgmaxq = avgstd = 0;
		maxmaxq = Double.NEGATIVE_INFINITY;
		minmaxq = Double.POSITIVE_INFINITY;
		
		esrange = new double[2];
		nsrange = new double[2];
		
		esrange[0] = Double.POSITIVE_INFINITY; // min 
		esrange[1] = Double.NEGATIVE_INFINITY; // max
		nsrange[0] = Double.POSITIVE_INFINITY; 
		nsrange[1] = Double.NEGATIVE_INFINITY;
		
		this.numStates = numStates;
		this.fiter = fiter;
	}
	
	protected double entropy(double[] q)
	{
		double ent = 0;
		for(double qx : q) {
			if(qx > 0)
				ent += qx * Math.log(qx) / Math.log(2);
		}
		return -ent;
	}
	
	protected double std(double[] q)
	{
		double mean = ArrayMath.sum(q) / q.length;
		double std = 0;
		for(double qx : q) 
			std += (qx - mean) * (qx - mean);
		return Math.sqrt(std);
	}
	
	public void count(AbstractSequence instance, FirstOrderFactorGraph model, int[] decoded)
	{
		int sid = instance.seqID;
		int length = instance.length;
		double[] q = new double[model.nodeMarginal[0].length];
		
		for(int i = 0; i < length; i++) {
			for(int s : fiter.states(sid, i)) {
				nsrange[0] = Math.min(nsrange[0], model.nodeScore[i][s]);
				nsrange[1] = Math.max(nsrange[1], model.nodeScore[i][s]);
				
				for(int sp : fiter.states(sid, i-1)) {
					esrange[0] = Math.min(esrange[0], model.edgeScore[i][s][sp]);
					esrange[1] = Math.max(esrange[1], model.edgeScore[i][s][sp]);
				}
			}
		}
		
		for(int i = 0; i < length; i++) {
			int best = -1;
			for(int s : fiter.states(sid, i)) {
				q[s] = Math.exp(model.nodeMarginal[i][s]);
				if(best < 0 || q[best] < q[s]) 
					best = s;
			}
			if(best == instance.tags[i]) ++ numCorrect;
			decoded[i] = best;
			
			avgent += entropy(q);
			avgstd += std(q);
			maxmaxq = Math.max(maxmaxq, q[best]);
			minmaxq = Math.min(minmaxq, q[best]);
			avgmaxq += q[best];
		}
		
		numTotal += length;
	}	
}
