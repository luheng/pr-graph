package data;

import gnu.trove.TIntArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class OCRCorpus extends AbstractCorpus {
	ArrayList<OCRSequence> instances;
	ArrayList<int[]> index2word;
	String[] index2tag;
	HashMap<String, Integer> ocrword2index;
	int[]  wordPoolSize;
	int[][] wordPool;

	public OCRCorpus(String corpusFileName, int maxNumNodes) throws 
		NumberFormatException, IOException {
		
		String currLine;
		BufferedReader reader = new BufferedReader(new FileReader(corpusFileName));
	
		instances = new ArrayList<OCRSequence>();
		index2word = new ArrayList<int[]>();
		
		TIntArrayList nodes = new TIntArrayList();
		TIntArrayList tags = new TIntArrayList();	
	
		maxSequenceID = 0;
		maxSequenceLength = 0;

		// for new sampling scheme, this is a quick fix
		ocrword2index = new HashMap<String, Integer>();
		wordPoolSize = new int[100];
		wordPool = new int[100][500];
		String ocrword = "";

		while ((currLine = reader.readLine()) != null) {
			String[] info = currLine.trim().split("\t");
			int nodeID = Integer.parseInt(info[0]) - 1;
			char letter = info[1].charAt(0) ;
			int nextID = Integer.parseInt(info[2]) - 1;
			int seqID = Integer.parseInt(info[3]) - 1;
			
			// this is a quick fix ...
			int foldID = Integer.parseInt(info[5]);
			if (foldID == 9) foldID = 1; 
			else foldID = 0;
			
			int[] pixels = new int[info.length - 6];
			for (int i = 6; i < info.length; i++) {
				pixels[i-6] = info[i].charAt(0) - '0';
			}
			index2word.add(pixels);
			nodes.add(nodeID);
			tags.add(letter - 'a');

			ocrword += letter;
			
			if (nextID < 0) {
				instances.add(new OCRSequence(this, seqID, foldID,
						nodes.toNativeArray(), tags.toNativeArray()));
				maxSequenceID = Math.max(maxSequenceID, seqID + 1);
				maxSequenceLength = Math.max(maxSequenceLength,
						nodes.size() + 1);
				if (nodes.size() >= maxNumNodes) {
					break;
				}
				nodes.clear();
				tags.clear();
				if (foldID == 0) {
					addToWordPool(ocrword, seqID);
				}

				ocrword = "";
			}
		}
		
		this.numNodes = index2word.size();
		this.numInstances = instances.size();
		this.numStates = 29;
		this.numTags = 26;
		this.numWords = numNodes;
		
		index2tag = new String[numStates];
		for(int i = 0; i < 26; i++) {
			index2tag[i] = Character.toString((char) ('a' + i));
		}
		index2tag[this.initialState = 26] = "-";
		index2tag[this.initialStateSO = 27] = "=";
		index2tag[this.finalState = 28] = "X";
 
		System.out.println("Number of OCR sequences: " + instances.size()
				+ "\tnumber of nodes: " + numNodes);
		reader.close();
		
		this.nodeFrequency = new int[numNodes];
		Arrays.fill(nodeFrequency, 1);
		System.out.println("read " + ocrword2index.size() + " distince words");
	}
	
	private void addToWordPool(String word, int seqID) {
		int wid = -1;
		if(ocrword2index.containsKey(word)) { 
			wid = ocrword2index.get(word);
		}
		else {
			wid = ocrword2index.size();
			ocrword2index.put(word, wid);
		}
		wordPool[wid][wordPoolSize[wid]++] = seqID;
	}
	
	@Override
	public OCRSequence getInstance(int id) {
		return instances.get(id);
	}

	public int[] getWord(int nid) {
		return index2word.get(nid);
	}
	
	public String getTag(int tid) {
		return index2tag[tid];
	}

	public String getTagSequence(int seqID) {
		int[] tags = instances.get(seqID).tags;
		String tagSeq = "";
		for(int t : tags) {
			tagSeq += (char) ('a' + t);
		}

		return tagSeq;	
	}

	@Override
	public void sampleFromFolderUnsorted(int numLabels, int seedFolder,
			Random sampler) {
        TIntArrayList trainIds = new TIntArrayList();
        TIntArrayList testIds = new TIntArrayList();
        boolean[][] sampled = new boolean[wordPool.length][wordPool[0].length];
        for (int i = 0; i < sampled.length; i++) {
			for(int j = 0; j < sampled[i].length; j++) {
	            sampled[i][j] = false;
			}
        }

        int pidx, sidx, currPool = 0;
        while (trainIds.size() < numLabels) {
            while (sampled[currPool][pidx = sampler
            		.nextInt(wordPoolSize[currPool])]) {
            	// Do nothing
            }
            trainIds.add(sidx = wordPool[currPool][pidx]);
            sampled[currPool][pidx] = true;
            getInstance(sidx).isLabeled = true;
			currPool = (currPool + 1) % ocrword2index.size();
        }

        for (int i = 0; i < numInstances; i++) {
            if (!getInstance(i).isLabeled) {
                testIds.add(i);
            }
        }

        trains = trainIds.toNativeArray();
        tests = testIds.toNativeArray();
        System.out.println("Number of trains:\t" + trains.length +
				"\ttests:\t" + tests.length);
    }
}
