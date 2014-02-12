package analysis;

import gnu.trove.TLongArrayList;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.text.SimpleDateFormat;

public class GeneralTimer {
	public HashMap<String, TLongArrayList> timestamps;
	public ArrayList<String> tags;
	private static SimpleDateFormat sdfDate =
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	public GeneralTimer() {
		timestamps = new HashMap<String, TLongArrayList>();
		tags = new ArrayList<String>();
	}
	
	public long stamp(String tag) {
		TLongArrayList stamps = null;
		if (timestamps.containsKey(tag)) {
			stamps = timestamps.get(tag);
		}
		else {
			stamps = new TLongArrayList();
			timestamps.put(tag, stamps);
			tags.add(tag);
		}
		long currTime = System.currentTimeMillis();
		long prevTime = stamps.isEmpty() ? currTime :
			stamps.get(stamps.size() - 1);
		stamps.add(currTime);
		System.out.println(String.format("[time::%s]\t", tag) +
				sdfDate.format(new Date()));
		return currTime - prevTime;
	}
	
	public void clear(String tag) {
		if (timestamps.containsKey(tag)) {
			timestamps.put(tag, null);
		}
	}
	
	public double mill2sec(long mills) {
		return (double) (mills / 1000);
	}
	
	public double[] getIntervals(String startTag, String endTag) {
		TLongArrayList s1 = timestamps.get(startTag);
		TLongArrayList s2 = timestamps.get(endTag);
		if (s1 == null || s2 == null) {
			return null;
		}
		int len = Math.min(s1.size(), s2.size());
		double[] intvs = new double[len];
		for (int i = 0; i < len; i++) {
			long t1 = s1.get(i);
			long t2 = s2.get(i);
			intvs[i] = mill2sec(t2 - t1);
		}
		return intvs;
	}

	public void printIntervalsInSecs(String startTag, String endTag) {
		double[] intvs = getIntervals(startTag, endTag);
		double tsum = .0;
		System.out.print(startTag+"/"+endTag+"::");
		for (double t : intvs) {
			System.out.print(String.format("\t%.0f", t));
			tsum += t;
		}
		System.out.println("\ttotal::\t" + tsum + " secs");
	}
	
	public void printIntervalsInMins(String startTag, String endTag) {
		double[] intvs = getIntervals(startTag, endTag);
		double tsum = .0;
		System.out.print(startTag+"/"+endTag+"::");
		for (double t : intvs) {
			System.out.print(String.format("\t%.2f", t / 60.0));
			tsum += t / 60.0;
		}
		System.out.println(String.format("\ttotal::\t%.2f\tmins", tsum));
	}
}
