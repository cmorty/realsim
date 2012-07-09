package de.fau.realsim.input;

import java.util.Calendar;

import org.apache.log4j.Logger;
import de.fau.realsim.*;

public class NetstatParser extends PacketInput {
	
	private static Logger logger = Logger.getLogger(NetstatParser.class);
	DataPacket dp = null;
	
	public NetstatParser(DataPacketHandler dph) {
		super(dph);
	}	
	

	
	public boolean receivingDatapacket(){
		return dp != null;
	}
	
	public void parse (String data){

		String lines[] = data.split("\\r?\\n");
		for(String line: lines){
			String[] el = line.split(" ");			
			if(el[0].equals("DAT:")){
				
				if(dp != null) {
					logger.warn("Unexpected message: " + line);
				}
				
				try {
					dp = new DataPacket(
						Integer.parseInt(el[1], 16),
						Integer.parseInt(el[2], 16),	
						Integer.parseInt(el[3], 16),
						Integer.parseInt(el[4], 16),
						Integer.parseInt(el[5], 16),
						Calendar.getInstance().getTimeInMillis()
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
			if(el[0].equals("---")){
				dp.trim();
				dph.handleDataPaket(dp);
				dp = null;
				continue;
			}
			
			try {
				Connection cn= new Connection(
					Integer.parseInt(el[0],16),
					Integer.parseInt(el[1],16),
					Integer.parseInt(el[2],16),
					Integer.parseInt(el[3],16),
					Integer.parseInt(el[4],16),
					Integer.parseInt(el[5],16)
				);
				dp.addConn(cn);
			} catch (Exception ex){
				logger.warn("Broken message: " + line);
			}
		}
	}
	
}
