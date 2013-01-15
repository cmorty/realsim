package de.fau.realsim;

import java.io.File;
import java.util.ArrayList;
import org.apache.log4j.*;

import de.fau.realsim.cmdhandlers.*;
import de.fau.realsim.util.ArgumentManager;

public class RealSimConsoleClient  {
	

	
	static ArgumentManager config = null;

	
	static String outfile = "network.dat";
	
	static CmdHandler cmdHandler[] = null;
	
	/**
	 * @param args
	 * @throws InterruptedException
	 */
	public static void main(String[] args_) throws InterruptedException {
		if (new File("log4j.prop").exists()){
			PropertyConfigurator.configure("log4j.prop");
		} else {
			BasicConfigurator.configure();
		}
		 
		config = new ArgumentManager();
		config.handleArguments(args_);
		
		ArrayList<CmdHandler> cmdH = new ArrayList<CmdHandler>();

		cmdH.add(new Convert(config));
		cmdH.add(new Analyze(config));
		cmdH.add(new Server(config));
		

		
		String args[] = config.getArguments();
		
		if(args.length == 0){
			System.out.print(printHelp(cmdH)); 
			System.exit(0);
		}
		CmdHandler cmd = null;
		for(CmdHandler cm : cmdH){
			if(cm.name.toLowerCase().equals(args[0].toLowerCase())){
				cmd = cm;
				break;
			}
		}
		
		if(cmd != null){
			int rv = cmd.main();
			System.exit(rv);
		} else {
			System.out.print(printHelp(cmdH)); 
		}
		
	}
	
	static String printHelp(ArrayList<CmdHandler> cmdh){
		String rv = new String("[ ");
		Boolean first = true;
		
		for(CmdHandler cmd : cmdh){
			if(!first){
				rv += " | ";				
			}
			first = false;
			rv += cmd.name;			
		}
		rv += " ]\n\n";
		
		for(CmdHandler cmd : cmdh){
			String [] help = cmd.help();
			rv += cmd.name + ": " + help[0] + " \n";
			rv += "\t" + help[1] + "\n";
			for(int i = 2 ; i < help.length; i++){
				rv+= "\t" + help[i] + "\n";
			}
			rv += "\n";
		}
		
		rv += "<Input>: \n";
		for(String t: CmdHandler.getIntypes()){
			rv+= "\t" + t + "\n";
		}
		
		
		rv += "\n<Output>: \n";
		for(String t: CmdHandler.getOuttypes()){
			rv+= "\t" + t + "\n";
		}
		
		
		return rv;
		
		
	}

/*	
	void main_parse() {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(args[1]));
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file: " + args[1]);
			System.exit(1);
		}
		String line = null;
		
		NetstatParser rsp = new NetstatParser(this);
		
		try {
			while ((line = reader.readLine()) != null) {
				rsp.parse(line);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	

	*/
	
	
	
	

	



}
