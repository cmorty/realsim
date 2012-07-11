package de.fau.realsim.output;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Vector;

import de.fau.realsim.AnalyzedData;
import de.fau.realsim.Connection;
import de.fau.realsim.DataPacket;
import de.fau.realsim.RealSimUtil;

public class RealSimFile implements PacketOutput{
	
	
	
	public static String getName() {
		return "rs";
	}

	
	public static String getHelp() {
		return "Data that can be imported using Realsim";
	}
	
	public boolean output(AnalyzedData anl, OutputStream os, int start, int end){
		PrintStream out = new PrintStream(os);
		for(int nid : anl.getNodeIds()){
			//Create node
			Vector<Integer> edges = new Vector<Integer>();
			Vector<Integer> curedges = new Vector<Integer>();
			out.println("0;addnode;" + RealSimUtil.idToStringInt(nid)  ); 
			AnalyzedData.NodeData nd = anl.getNodeData(nid);
			int lts = 0;
			
					
			for(DataPacket dp : nd.packets){
				int ts = anl.getNormalizedTime(dp);
			
				if(ts < start) continue;
				if(ts > end) break;
			
				ts -= start;
				
				for(Connection cnn : dp.getCns()){
					out.printf("%d;setedge;%s;%s;%f;%d;%d\n", 
							ts,
							RealSimUtil.idToStringInt(cnn.node), 
							RealSimUtil.idToStringInt(dp.src), 
							((float)cnn.rcv)/(cnn.rcv+cnn.loss),
							cnn.rssi,
							cnn.lqi);
					curedges.add(cnn.node);
				}
				if(lts + 10 < ts){
					for(Integer e : edges){
						if(! curedges.contains(e)){
							out.printf("%d;rmedge;%s;%s\n", ts,
									RealSimUtil.idToStringInt(e), 
									RealSimUtil.idToStringInt(dp.src));
						}
					}
					edges = curedges;
					curedges = new Vector<Integer>();
					lts = ts;
				}
				
			}
		}
		return true;
	}

	@Override
	public boolean output(AnalyzedData anl, OutputStream os) {
		return output(anl, os, 0, Integer.MAX_VALUE);
	}


}
