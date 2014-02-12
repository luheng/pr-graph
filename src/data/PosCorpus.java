package data;

import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;

import config.PosConfig;


public class PosCorpus extends AbstractCorpus {
	ArrayList<PosSequence> instances;
	ArrayList<String> index2word; 
	TObjectIntHashMap<String> word2index;
	ArrayList<String> index2tag;
	TObjectIntHashMap<String> tag2index;
	TObjectIntHashMap<String> utag2index;
	public int[] umap; // tag id to universal tag id
	public NGramMapper ngrams;
	PosConfig config;
	public int numTokens;
	
	public PosCorpus(String[] corpusFiles, NGramMapper ngmap,
			PosConfig config) throws NumberFormatException, IOException {
		this.config = config;
		RegexHelper.setLanguage(config.langName);

		instances = new ArrayList<PosSequence>();
		index2word = new ArrayList<String>();
		word2index = new TObjectIntHashMap<String>();
		index2tag = new ArrayList<String>();
		tag2index = new TObjectIntHashMap<String>();
		maxSequenceID = 0;
		maxSequenceLength = 0;
		numTokens = 0;
		
		loadTags(corpusFiles);
		loadUniversalTagMap(config.umapPath);
		
		for (int i = 0; i < corpusFiles.length; i++) {
			loadFromCoNLLFile(corpusFiles[i], i);
		}
		
		remapUniversalTags();
		numWords = index2word.size();
		numTags = tag2index.size();
		
		initialState = map(tag2index, index2tag, "<t0>");
		initialStateSO = map(tag2index, index2tag, "<t00>");
		finalState = map(tag2index, index2tag, "</t0>");
		numStates = index2tag.size(); // plus one initial state 
		numInstances = instances.size();
		numNodes = 0; // if not using ngram index
		
		System.out.println(String.format(
				"Number of sentences: %d\n" +
				"Number of words: %d\n" +
				"Number of tokens: %d\n" +
				"Number of hidden states: %d\n", instances.size(),
				index2word.size(), numTokens, index2tag.size()));
		
		if (ngmap != null) {
			this.ngrams = ngmap;
			this.nodeFrequency = new int[ngmap.index2ngram.size()];			
			for (PosSequence instance : instances) {
				instance.nodes = ngmap.getNodes(this, instance);
				for (int nid : instance.nodes) {
					if (nid >= 0) nodeFrequency[nid] ++;
				}
			}
			this.numNodes = ngmap.index2ngram.size();
		}
	}
	
	private void loadTags(String[] files) throws IOException {
		for (String corpusFileName : files) {
			String currLine;
			BufferedReader reader = new BufferedReader(new FileReader(
					corpusFileName));	
			while ((currLine = reader.readLine()) != null) {			
				currLine = reader.readLine();
				if(currLine == null) break;
				String[] tagInfo = currLine.trim().split("\t");	
				for(String tag : tagInfo) {
					map(tag2index, index2tag, tag.trim());
				}
				reader.readLine(); // skip dependency info
				reader.readLine(); // skip empty line
			}
			reader.close();
		}
		System.out.println("Read " + tag2index.size() + " tags.");
	}
	
	private void loadUniversalTagMap(String univTagPath) throws IOException	{
		utag2index = new TObjectIntHashMap<String>();
		for (int i = 0; i < UniversalTagSet.tags.length; i++) { 
			utag2index.put(UniversalTagSet.tags[i], i);
		}
		umap = new int[index2tag.size()];
		Arrays.fill(umap, -1);
	
		String currLine;
		BufferedReader reader = new BufferedReader(new FileReader(univTagPath));
		while ((currLine = reader.readLine()) != null) {	
			String[] info = currLine.split("\t");
			String t = info[0].trim();
			String ut = info[1].trim();
			if (!tag2index.contains(t) || !utag2index.contains(ut)) {
				continue;
			}
			umap[tag2index.get(t)] = utag2index.get(ut);
		}
		for (int i = 0; i < index2tag.size(); i++) { 
			if(umap[i] < 0) { 
				umap[i] = utag2index.get("X");
			}
		}
		reader.close();
	}
	
	private void remapUniversalTags() {
		tag2index.clear();
		index2tag.clear();
		for (int i = 0; i < UniversalTagSet.tags.length; i++) {
			String utag = UniversalTagSet.tags[i];
			tag2index.put(utag, i);
			index2tag.add(utag);
		}
		for (PosSequence instance : instances) {
			for(int i = 0; i < instance.length; i++) {
				instance.tags[i] = umap[instance.tags[i]];
			}
		}
	}
	
	public PosSequence getInstance(int id) {
		return instances.get(id);
	}
	
	@Deprecated
	protected void loadFromFile(String corpusFileName, int foldID)
			throws IOException {
		String currLine;
		BufferedReader reader = new BufferedReader(new InputStreamReader(
 	               new FileInputStream(corpusFileName), config.encoding));
		while ((currLine = reader.readLine()) != null) {
			String[] wordInfo = currLine.trim().split("\t");
			currLine = reader.readLine();
			if(currLine == null) break;
			String[] tagInfo = currLine.trim().split("\t");
			TIntArrayList words = new TIntArrayList();
			TIntArrayList tags = new TIntArrayList();
			
			for (int i = 0; i < wordInfo.length; i++) {
				String token = wordInfo[i].trim();	
				words.add(map(word2index, index2word, token));
				tags.add(tag2index.get(tagInfo[i].trim()));
			}
		
			if (words.size() > 0) {
				int seqID = instances.size();
				PosSequence instance = new PosSequence(this, seqID, foldID, 
						words.toNativeArray(), tags.toNativeArray());
				instances.add(instance);
				numTokens += instance.length;
				
				maxSequenceID = Math.max(maxSequenceID, seqID + 1);
				maxSequenceLength = Math.max(maxSequenceLength,
						words.size() + 1);
			}
			reader.readLine(); // skip dependency info
			reader.readLine(); // skip empty line
		}
		reader.close();
	}
	
	protected void loadFromCoNLLFile(String corpusFileName, int foldID)
			throws IOException {
		String currLine;
		BufferedReader reader = new BufferedReader(new InputStreamReader(
 	               new FileInputStream(corpusFileName), config.encoding));
		
		TIntArrayList wordIds = new TIntArrayList();
		TIntArrayList tagIds = new TIntArrayList();
		while ((currLine = reader.readLine()) != null) {
			String[] info = currLine.trim().split("\t");
			if (info.length < 5) {
				if (wordIds.size() > 0) {
					int seqID = instances.size();
					PosSequence instance = new PosSequence(this, seqID, foldID, 
							wordIds.toNativeArray(), tagIds.toNativeArray());
					instances.add(instance);
					numTokens += instance.length;
					maxSequenceID = Math.max(maxSequenceID, seqID + 1);
					maxSequenceLength = Math.max(maxSequenceLength,
						instance.length + 1);
					wordIds.clear();
					tagIds.clear();
				}
			} else {
				wordIds.add(map(word2index, index2word, info[1]));
				tagIds.add(tag2index.get(info[4].trim()));
			}
		}
		reader.close();
	}

	protected int map(TObjectIntHashMap<String> node2index,
			ArrayList<String> index2node, String node) {
		if(node2index.contains(node)) {
			return node2index.get(node);
		}
		
		int idx = index2node.size();
		index2node.add(node);
		node2index.put(node, idx);
		return idx;
	}
	
	public String getWord(int wordID) {
		return wordID >= 0 && wordID < index2word.size() ?
				index2word.get(wordID) : "<unk>";
	}
	
	public String getTag(int tagID) {
		return tagID >= 0 && tagID < index2tag.size() ?
				index2tag.get(tagID) : "<unk_t>";
	}
	
	public int lookupWord(String word) {
		return word2index.contains(word) ? word2index.get(word) : -1;
	}
	
	public int lookupTag(String tag) {
		return tag2index.contains(tag) ? tag2index.get(tag) : -1;	
	}
	
	public String getPrintableWord(int wordID) {
		return getWord(wordID);
	}
	
	public String getPrintableTag(int tagID) {
		return getTag(tagID);
	}
}
