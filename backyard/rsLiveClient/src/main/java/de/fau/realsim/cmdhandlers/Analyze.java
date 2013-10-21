package de.fau.realsim.cmdhandlers;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import de.fau.realsim.AnalyzedData;
import de.fau.realsim.DataPacket;
import de.fau.realsim.input.PacketInput;
import de.fau.realsim.util.ArgumentManager;

public class Analyze extends CmdHandler {
	
	public Analyze(ArgumentManager am) {
		super(am, "analyze");
	
	}

	@Override
	public String[] help() {
		ArrayList<String> rv = new ArrayList<String>();	
		rv.add("<Input>");
		rv.add("Anylze File");
		rv.add(helpPI);
		return rv.toArray(new String[0]);
	}
	
	@Override
	public int main() {
		
		if(args.length < 2){
			System.err.print("Inputfile missing");
			return 1;
		}
		
		PacketInput pi = getPacketInput();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(args[1]);
			pi.parse(fis);
			fis.close();
		} catch (IOException e) {
			System.err.println(e);
			System.exit(1);
		}
		System.out.println("LEN:" + pkts.size());
		AnalyzedData anl = new AnalyzedData(pkts.toArray(new DataPacket[0]));
		System.out.println("LEN:" + anl.origdata.length);
		anl.analyze();
		System.out.println("LEN:" + anl.getPackets().length);
		System.out.append(anl.getStats());
			
		return 0;
	}
	
}
