package de.fau.realsim.input;

import org.apache.log4j.Logger;

import de.fau.realsim.Connection;
import de.fau.realsim.DataPacket;
import de.fau.realsim.DataPacketHandler;

public class SerialdumpNetstatParser extends NetstatParser {
	private static Logger logger = Logger.getLogger(SerialdumpNetstatParser.class);

	public SerialdumpNetstatParser(DataPacketHandler dph) {
		super(dph);
	}
	
	
	static public String getName() {
		return "netDump";
	}

	
	static public String getHelp() {
		return "Data Provided by NetStat using serialdump";
	}
	
	
	public void parse (String data){
		String lines[] = data.split("\\r?\\n");
		for(String line: lines){
			String[] el = line.split(" ");			
			if(el[1].equals("DAT:")){
				
				if(dp != null) {
					logger.warn("Unexpected message: " + line);
				}
				
				try {
					dp = new DataPacket(
						Integer.parseInt(el[2], 16),
						Integer.parseInt(el[3], 16),	
						Integer.parseInt(el[4], 16),
						Integer.parseInt(el[5], 16),
						Integer.parseInt(el[6], 16),
						Long.parseLong(el[0], 10)
					);
				} catch (Exception ex) {
					dp = null;
					logger.warn("Broken message: " + line + "\n" + ex.toString());
				} 
				continue;
			}
			if(dp == null){
				//logger.warn("Received data, but no DataBlock: " + line);
				continue;
			}
			if(el[1].equals("---")){
				dp.trim();
				dph.handleDataPaket(dp);
				dp = null;
				continue;
			}
			
			try {
				Connection cn= new Connection(
					Integer.parseInt(el[1],16),
					Integer.parseInt(el[2],16),
					Integer.parseInt(el[3],16),
					Integer.parseInt(el[4],16),
					Integer.parseInt(el[5],16),
					Integer.parseInt(el[6],16)
				);
				dp.addConn(cn);
			} catch (Exception ex){
				logger.warn("Broken message: " + line);
			}
		}
	}
	
	
}
