package graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import config.PosGraphConfig;
import util.ArrayMath;
import gnu.trove.TIntDoubleHashMap;
import data.PosCorpus;
import data.PosSequence;
import data.UniversalTagSet;
import data.RegexHelper;

public class PosGraphBuilder {
	PosCorpus corpus;
	PosGraphConfig config;
	SuffixDictionary suffixDict;
	
	CountDictionary ngramCounts, featureCounts;
	int[][] token2ngram, ngram2utag;
	ArrayList<TIntDoubleHashMap> jointCounts;
	
	int[][] features;
	double[][] featureVals;
	int numNGrams;
	int numFeatures;
	int numNGramOccur;
	int numFeatureOccur;
	int numFeatureTemplates;
	int ngramSize;
	int contextSize;
	
	int[][] wordFeatureTemplates, suffixFeatureTemplates;
	String[] featurePrefixes = {"w12","w13","w23", "w2", "w2s3","w2s1", "w01",
			"w34","w0134", "w012", "w234", "w024", "w023", "w124", "w02", "w24"};
	
	public PosGraphBuilder(PosCorpus corpus, PosGraphConfig config) {
		this.corpus = corpus;
		this.config = config;
		this.ngramSize = config.ngramSize;
		this.contextSize = config.contextSize;
		this.numFeatureTemplates = featurePrefixes.length;
		
		try {
			this.suffixDict = SuffixDictionary.fromSuffixFile(
					config.suffixPath, config.langName, config.encoding);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void buildGraph() {
		generateFeatureTemplates();
		extractNGrams();
		extractFeatures();
		
		KNNGraphConstructer ebuilder = new KNNGraphConstructer(
				ngramCounts, features, featureVals, 
				config.numNeighbors, config.mutualKNN, config.minSimilarity,
				config.graphPath, config.ngramPath, config.numThreads);
		try {
			ebuilder.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void generateFeatureTemplates() {
		Pattern pattern = Pattern.compile("[a-z][0-9]+");
		int offset = (contextSize - 1) / 2;
	
		wordFeatureTemplates = new int[numFeatureTemplates][];
		suffixFeatureTemplates = new int[numFeatureTemplates][];
		
		for(int i = 0; i < numFeatureTemplates; i++) {
			String prefix = featurePrefixes[i];
			Matcher matcher = pattern.matcher(prefix);
			while(matcher.find()) {
				String fstr = matcher.group();
				int flen = fstr.length() - 1;
				if(fstr.charAt(0) == 'w') {
					wordFeatureTemplates[i] = new int[flen];
					for(int j = 1; j <= flen; j++) {
						wordFeatureTemplates[i][j - 1] =
								(fstr.charAt(j) - '0') - offset;
					}
				}
				else if(fstr.charAt(0) == 's') {
					suffixFeatureTemplates[i] = new int[flen];
					for(int j = 1; j <= flen; j++) {
						suffixFeatureTemplates[i][j - 1] =
								(fstr.charAt(j) - '0') - offset;
					}
				}
			}
		}
	}
		
	private void extractNGrams() {
		if(ngramCounts == null) {
			ngramCounts = new CountDictionary();
			token2ngram = new int[corpus.numInstances][];
			for(int sid = 0; sid < corpus.numInstances; sid++) {
				token2ngram[sid] = new int[corpus.getInstance(sid).length];
			}
		}
		
		for(int sid = 0; sid < corpus.numInstances; sid ++) {
			PosSequence instance = corpus.getInstance(sid);
			String[] words = getWords(instance.tokens, ngramSize / 2);
			for(int i = 0; i < instance.length; i++) {
				if(RegexHelper.isPunctuation(corpus.getWord(
						instance.tokens[i]))) {
					token2ngram[sid][i] = -1;
					continue;
				}
				String ng = getNGram(words, i);
				token2ngram[sid][i] = ngramCounts.addOrUpdate(ng);
			}
		}
		numNGrams = ngramCounts.size();
	}
	
	private void extractFeatures() {
		if(featureCounts == null) {
			featureCounts = new CountDictionary();
			features = new int[numNGrams][];
			featureVals = new double[numNGrams][];
			jointCounts = new ArrayList<TIntDoubleHashMap>();
			
			for(int i = 0; i < numNGrams; i++) {
				jointCounts.add(new TIntDoubleHashMap());
			}
			numFeatureOccur = 0;
			numNGramOccur = 0;
			ngram2utag = new int[numNGrams][UniversalTagSet.tags.length];
			for(int i = 0; i < numNGrams; i++) {
				for(int j = 0; j < ngram2utag[i].length; j++) {
					ngram2utag[i][j] = 0;
				}
			}
		}
		ngramCounts.resetCounts();
		for(int sid = 0; sid < corpus.numInstances; sid++) {
			PosSequence instance = corpus.getInstance(sid);
			String[] context = getWords(instance.tokens, contextSize / 2);
			
			for(int i = 0; i < instance.length; i++) {
				int nid = token2ngram[sid][i];
				if(nid < 0) {
					continue;
				}
				ngramCounts.update(nid);
				addFeatures(context, i, nid);
				++ numNGramOccur;
				++ ngram2utag[nid][corpus.umap[instance.tags[i]]];
			}
		}
	
		System.out.println(String.format("Extracted %d %d-grams. " +
				"Total occur: %d", numNGrams, ngramSize, numNGramOccur));
		
		numFeatures = featureCounts.size();
		System.out.println(String.format("Extracted %d features from %d " +
				"sentences.", numFeatures, corpus.numInstances));

		for(int nid = 0; nid < numNGrams; nid++) {
			TIntDoubleHashMap fv = jointCounts.get(nid);
			int len = fv.size();
			features[nid] = fv.keys();
			featureVals[nid] = new double[len];
			Arrays.sort(features[nid]);
			
			double logX = (Math.log(ngramCounts.getFrequency(nid)) -
					Math.log(numNGramOccur)) / Math.log(2); 
			double l2norm = 0.0;
			
			for(int j = 0; j < len; j++) {
				int fid = features[nid][j];
				double logY = Math.log(featureCounts.getFrequency(fid)) /
						Math.log(2);
				double logXY = Math.log(fv.get(fid)) / Math.log(2);
				
				featureVals[nid][j] = logXY - logX - logY;
				l2norm += featureVals[nid][j] * featureVals[nid][j]; 
			}
			ArrayMath.L2Norm(featureVals[nid]);
			l2norm =  Math.sqrt(l2norm);
			for(int j = 0; j < len; j++) {
				featureVals[nid][j] /= l2norm;
			}
		} 
		jointCounts = null;
	}
	
	private void addFeatures(String[] words, int offset, int ngramID) {
		TIntDoubleHashMap fv = jointCounts.get(ngramID);
		int center = (contextSize - 1) / 2 + offset;
		
		for (int i = 0; i < numFeatureTemplates; i++) {
			String feat = featurePrefixes[i] + "=";
			if (wordFeatureTemplates[i] != null) { 
				for (int j : wordFeatureTemplates[i]) {
					feat += "<W>=" + words[j + center].toLowerCase();
				}
			}
			if (suffixFeatureTemplates[i] != null) {
				for (int j : suffixFeatureTemplates[i]) {
					feat += "<SF>=" + suffixDict.getLongestSuffix(
							words[j + center]);
				}
			}
			int fid = featureCounts.addOrUpdate(feat);
			fv.adjustOrPutValue(fid, 1, 1);
		}
	}
		
	private String normalize(String tok) {
		return RegexHelper.isNumerical(tok) ? "<num>" : tok;
	}
	
	private String getNGram(String[] words, int offset) {
		String ng = words[offset];
		for(int j = 1; j < ngramSize; j++) {
			ng += " " + words[offset + j];
		}
		return ng;
	}
	
	private String[] getWords(int[] words, int padding) {
		String[] wstr = new String[words.length + padding * 2];
		int ptr = 0;
		
		for(int i = padding - 1; i >= 0; i--) {
			wstr[ptr++] = "<s" + i + ">";
		}
		for(int wid : words) {
			wstr[ptr++] = normalize(corpus.getWord(wid));
		}
		for(int i = 0; i < padding; i++) {
			wstr[ptr++] = "</s" + i + ">";
		}
		return wstr;
	}
}
