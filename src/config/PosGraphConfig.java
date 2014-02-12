package config;

import java.io.PrintStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class PosGraphConfig extends PosConfig {
	@Option(name = "-suffix-path", usage="")
	public String suffixPath = "/home/luheng/Working/pr-graph/data/suffix.dict";
	
	@Option(name = "-num-neighbors", usage="")
	public int numNeighbors = 60;
	
	@Option(name = "-context-size", usage="")
	public int contextSize = 5;

	@Option(name = "-mutual", usage="")
	public boolean mutualKNN = true;

	@Option(name = "-min-sim", usage="")
	public double minSimilarity = 0.00;
	
	public PosGraphConfig(String[] args) {
		super(args);
		CmdLineParser parser = new CmdLineParser(this);
		parser.setUsageWidth(120);
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			e.printStackTrace();
		}
	}

	public void print(PrintStream ostr) {
		ostr.println("-suffix-path\t" + suffixPath);
		ostr.println("-context-path\t" + contextSize);
		ostr.println("-mutual\t" + mutualKNN);
		ostr.println("-min-sim\t" + minSimilarity);
		super.print(ostr);
	}
}
