package data;

import gnu.trove.TObjectIntHashMap;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import config.PosConfig;

public class NGramMapper {
	public ArrayList<String> index2ngram;
	public TObjectIntHashMap<String> ngram2index;
	public int ngramSize;
	
	public NGramMapper(PosConfig config)
			throws IOException {
		index2ngram = new ArrayList<String>();
		ngram2index = new TObjectIntHashMap<String>();
		ngramSize = config.ngramSize;

		BufferedReader reader = new BufferedReader(new InputStreamReader(
	               new FileInputStream(config.ngramPath), config.encoding));
		String currLine;
		while ((currLine = reader.readLine()) != null) {	
			String[] info = currLine.trim().split("\t");
	
			int index = Integer.parseInt(info[0]);
			String ngram = info[1].trim();
			index2ngram.add(ngram);
			ngram2index.put(ngram, index - 1);
		}
 
		reader.close();
	}
	
	private String normalize(String tok) {
		return RegexHelper.isNumerical(tok) ? "<num>" : tok;
	}
	
	public int[] getNodes(PosCorpus corpus, PosSequence instance) {
		int len = instance.length;
		int head = (ngramSize - 1) / 2;
		int tail = ngramSize / 2;
		int[] nodes = new int[len];
		String[] wstr = new String[len + head + tail];
		int ptr = 0;
		
		for (int i = head - 1; i >= 0; i--) {
			wstr[ptr++] = "<s" + i + ">";
		}
		
		for (int i = 0; i < len; i++) {
			wstr[ptr++] = normalize(corpus.getWord(instance.tokens[i]));
		}
		
		for (int i = 0; i < tail; i++) {
			wstr[ptr++] = "</s" + i + ">";
		}
		
		for(int i = 0; i < instance.length; i++) {
			String ngram = wstr[i];
			for (int j = 1; j < ngramSize; j++) {
				ngram += " " + wstr[i + j];
			}
			nodes[i] = ngram2index.contains(ngram) ?
					ngram2index.get(ngram) : -1;
		}
		return nodes;
	}
}
