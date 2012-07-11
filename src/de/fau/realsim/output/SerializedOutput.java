package de.fau.realsim.output;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import de.fau.realsim.AnalyzedData;
import de.fau.realsim.DataPacket;

public class SerializedOutput implements PacketOutput{

	
	public static String getName() {
		return "ser";
	}

	
	public static String getHelp() {
		return "Serialized Data";
	}
	
	@Override
	public boolean output(AnalyzedData anl, OutputStream os, int start, int end) {
		DataPacket[] pkts = anl.getPackets();
		return output(pkts, os);
		
	}
	
	public static boolean output(DataPacket[] pkts, OutputStream os){
		ObjectOutputStream o;
		try {
			o = new ObjectOutputStream(os);
			o.writeObject(pkts.clone());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public boolean output(AnalyzedData anl, OutputStream os) {
		return output(anl, os, 0, Integer.MAX_VALUE);
	}


	
	
}
