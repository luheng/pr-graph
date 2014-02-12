package config;

import java.io.PrintStream;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class PosConfig extends Config {
	@Option(name = "-ngram-path", usage="")
	public String ngramPath = "./data/graph/es-60nn-dep.idx";
	
	@Option(name = "-umap-path", usage="")
	public String umapPath = "./data/univmap/es-cast3lb.map";
	
	@Option(name = "-lang-name", usage="")
	public String langName = "spanish";
	
	// Due to a bug in the code, set the encoding to LATIN1 to recreate
	// experiment results in the CoNLL'13 paper.
	@Option(name = "-encoding", usage="")
	public String encoding = "UTF8";

	@Option(name = "-ngram-size", usage="")
	public int ngramSize = 3;
	
	public PosConfig(String[] args)
	{
		super();
		CmdLineParser parser = new CmdLineParser(this);
		parser.setUsageWidth(120);
		
		try {
			parser.parseArgument(args);
			langName = langName.toLowerCase();
		} catch (CmdLineException e) {
			e.printStackTrace();
		}
	}
	
	public void print(PrintStream ostr)	{
		ostr.println("-ngram-path\t" + ngramPath);
		ostr.println("-umap-path\t" + umapPath);
		ostr.println("-lang-name\t" + langName);
		ostr.println("-encoding\t" + encoding);
		ostr.println("-ngram-size\t" + ngramSize);
		super.print(ostr);
	}
}
