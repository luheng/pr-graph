package programs;

import java.io.IOException;

import config.PosGraphConfig;
import data.PosCorpus;
import graph.PosGraphBuilder;
import util.MemoryTracker;

public class TestPosGraphBuilder {
	public static void main(String[] args) throws NumberFormatException,
			IOException	{
		PosGraphConfig config = new PosGraphConfig(args);
		config.print(System.out);			

		MemoryTracker mem  = new MemoryTracker();
		mem.start(); 
		
		String[] dataFiles = config.dataPath.split(",");
		PosCorpus corpus = new PosCorpus(dataFiles, null, config);
		PosGraphBuilder builder = new PosGraphBuilder(corpus, config);
		builder.buildGraph();
		
		mem.finish();
		System.out.println("Memory usage:: " + mem.print());
	}
}
