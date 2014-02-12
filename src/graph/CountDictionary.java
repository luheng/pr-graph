package graph;

import java.util.ArrayList;

import gnu.trove.TIntIntHashMap;
import gnu.trove.TObjectIntHashMap;

public class CountDictionary {
	public TObjectIntHashMap<String> str2index;
	public ArrayList<String> index2str;
	public TIntIntHashMap index2count;
	
	public CountDictionary() {
		str2index = new TObjectIntHashMap<String>();
		index2str = new ArrayList<String>();
		index2count = new TIntIntHashMap();
	}
	
	public int addOrUpdate(String str) {
		int sid;
		if(str2index.contains(str)) { 
			sid = str2index.get(str);
			index2count.adjustValue(sid, 1);
		}
		else {
			sid = index2str.size();
			str2index.put(str, sid);
			index2str.add(str);
			index2count.put(sid, 1);
		}
		return sid;
	}
	
	public int update(String str) {
		if(!str2index.contains(str)) {
			return -1;
		}
		int sid = str2index.get(str);
		index2count.adjustValue(sid, 1);
		return sid;
	}
	
	public int update(int sid) {
		if(sid < 0 || sid >= index2str.size()) {
			return -1;
		}
		index2count.adjustValue(sid, 1);
		return sid;
	}
	
	public void resetCounts() {
		for(int k : index2count.keys()) {
			index2count.put(k, 0);
		}
	}
	
	public int getIndex(String str) {
		return str2index.contains(str) ? str2index.get(str) : -1;
	}

	public double getFrequency(int nid) {
		return index2count.get(nid);
	}
	
	public int size() {
		return index2str.size();
	}

}
