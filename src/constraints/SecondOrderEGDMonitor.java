package constraints;

import models.AbstractFactorIterator;
import models.SecondOrderFactorGraph;
import data.AbstractSequence;

public class SecondOrderEGDMonitor extends FirstOrderEGDMonitor {
	
	AbstractFactorIterator fiter;
	
	public SecondOrderEGDMonitor(int numStates, AbstractFactorIterator fiter)
	{
		super(numStates, fiter);
		this.fiter = fiter;
	}
	
	public void count(AbstractSequence instance, SecondOrderFactorGraph model, int[] decoded)
	{
		int sid = instance.seqID;
		int length = instance.length;
		double[] q = new double[model.nodeMarginal[0].length];
		
		for(int i = 0; i < length; i++) {
			for(int s : fiter.states(sid, i)) {
				nsrange[0] = Math.min(nsrange[0], model.nodeScore[i][s]);
				nsrange[1] = Math.max(nsrange[1], model.nodeScore[i][s]);
				
				for(int sp : fiter.states(sid, i-1))
					for(int spp : fiter.states(sid, i-2)) {
						esrange[0] = Math.min(esrange[0], model.edgeScore[i][s][sp][spp]);
						esrange[1] = Math.max(esrange[1], model.edgeScore[i][s][sp][spp]);
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
