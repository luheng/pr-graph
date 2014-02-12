package config;

import java.io.PrintStream;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

public class OCRConfig extends Config {
	public OCRConfig(String[] args) {
		super();
		CmdLineParser parser = new CmdLineParser(this);
		parser.setUsageWidth(120);
		
		try {
			parser.parseArgument(args);
		} catch (CmdLineException e) {
			e.printStackTrace();
		}
	}
	
	public void print(PrintStream ostr) {
		super.print(ostr);
	}
}
