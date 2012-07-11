package de.fau.realsim;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Vector;
import java.util.WeakHashMap;

import org.apache.log4j.Logger;

public class AnalyzedData {
	
	public class NodeData{
		public int id;
		public Vector<DataPacket> packets = new Vector<DataPacket>(); 
		public int offset_sink = Integer.MAX_VALUE;
		public int reboots = 0;
		public int packets_lost = 0;
		public int dups = 0;
		public int moved = 0;
		
	}
	
	WeakHashMap<DataPacket, Integer> normalized_time = new WeakHashMap<DataPacket, Integer>(); 
	
	private static Logger logger = Logger.getLogger(AnalyzedData.class);
	public final DataPacket[] origdata;
	private DataPacket[] data = null;
	private HashMap<Integer, NodeData> nodedata = null; //Datapackets by Node
	public int packet_first_last; //Last "first" packet (normalized time)
	public int packet_last_first; //First "last" packet (normalized time)
	public int packet_last; //Last packet (normalized time)
	
	
	public int sinkreboot = 0;
	
	public AnalyzedData(DataPacket[] data){
		origdata = data.clone();
	}
	

	
	public void analyze(){
		//Check sink time stamp
		if(origdata.length < 2 ) {
			return;
		}

		// Duplicate Data to mess with
		data = origdata.clone();
		
		
		
		nodedata = new HashMap<Integer, NodeData>();
		
		
		//Sort packets by node
		
		for(int i = 0; i < data.length; i++){
			
			NodeData nd = nodedata.get(data[i].src);
			if(nd == null){
				nd = new NodeData();
				nd.id = data[i].src;
				nodedata.put(data[i].src, nd);
			}
			nd.packets.add(data[i]);
		}
		

				
		
		int tsoff = data[0].hts - data[0].ts;
		
		
		
		//Search for sink Reboots
		
		if(data[0].hts != 1337174016){
			//Host timestamp ok
			for(DataPacket dp : data){
				logger.debug("" + dp.hts + "\t" + dp.ts + "\t" + (dp.hts -tsoff- dp.ts));
				if(Math.abs(dp.hts - tsoff - dp.ts) > 2){
					sinkreboot ++;				
					logger.info("Sink rebooted at " + new Integer(dp.hts).toString() );
				}
				tsoff = dp.hts - dp.ts;
			}
		} else {
			int ts = 0;
			for(DataPacket dp : data){
				if(dp.ts < ts){
					sinkreboot ++;				
					logger.info("Sink rebooted at " + new Integer(ts).toString() );
				}
				ts = dp.ts;
			}
		}
		
		
		
		// Look at motes
		
		// Bring to right order
		for(NodeData nd : nodedata.values()){
			int lastid = 0;
			int lastts = 0;
			for(int i = 0; i < nd.packets.size(); i++){
				DataPacket dp = nd.packets.get(i);
				if(dp.sts < lastts){
					logger.info("Timestamp in past");
					
				}
				
				if(dp.id <= lastid){
					for(int sback = i - 1; sback >= 0; sback-- ){
						DataPacket sdp = nd.packets.get(sback);
						if(sdp.id == dp.id){
							if(!sdp.same(dp)){
								logger.warn("Packet with same ID and different contents");
							}
							logger.info("("+ RealSimUtil.idToStringInt(nd.id)  +" ) Duplicate - removing");
							nd.packets.remove(i);
							nd.dups++;
							i--;
							break;
						} else if(sdp.id < dp.id) {
							logger.info("("+ RealSimUtil.idToStringInt(nd.id)  +" ) Found position. Moving " + i + " to "+ (sback + 1));
							nd.packets.remove(i);
							nd.packets.insertElementAt(dp, sback + 1);
							nd.moved++;
							break;
						} else if(sback == 0){
							logger.info("("+ RealSimUtil.idToStringInt(nd.id)  +" ) Found position. Moving " + i + " to "+ (sback));
							nd.packets.remove(i);
							nd.packets.insertElementAt(dp, sback);
							nd.moved++;
						}
					} 
					//Do not set lastid
					 
				} else {
					lastid = dp.id;
				}
				
			}
		}

		/*
		 * Find the minimum lost packets. Done after sorted
		 */
		for(NodeData nd : nodedata.values()){
			int lastid = nd.packets.get(0).id - 1 ;
						
			for(DataPacket dp : nd.packets){
				if( lastid >= dp.id){
					logger.error("Something went wrong here: Lastid: " + lastid + " ID: " + dp.id);
					System.exit(1);
				}
				nd.packets_lost +=  dp.id  - (lastid + 1); 
				lastid = dp.id;
				
			}
		}
		
		
		
		/*
		 * Find the minimal offset between sink and node
		 */		
		
		
		for(NodeData nd : nodedata.values()){
			int min = Integer.MAX_VALUE ;
			
			for(DataPacket dp : nd.packets){
				int diff = dp.ts - dp.sts;
				if(diff < min)  min = diff;
			} 
			nd.offset_sink = min;
		}	
		

		/*
		 * Bring to normalized time base, starting with 0
		 */
		
		{
			//Find extremes of the first packet
			int firstp = Integer.MAX_VALUE;
			int lastp = Integer.MIN_VALUE;
			
			for(NodeData nd : nodedata.values()){
					int tsfp = nd.packets.get(0).sts + nd.offset_sink; 
					if(tsfp  < firstp) firstp = tsfp;
					if(tsfp > lastp) lastp = tsfp;
			}
			
			
			//Normalize
			
			packet_first_last = lastp - firstp;
			
			for(NodeData nd : nodedata.values()){
				for(DataPacket dp : nd.packets){
					normalized_time.put(dp, dp.sts + nd.offset_sink - firstp);
				}
			}
		}
		
		{
			//Find extremes of the last packet
			int firstp = Integer.MAX_VALUE;
			int lastp = Integer.MIN_VALUE;
			
			for(NodeData nd : nodedata.values()){
					int tsfp = nd.packets.get(nd.packets.size() -1 ).sts + nd.offset_sink; 
					if(tsfp  < firstp) firstp = tsfp;
					if(tsfp > lastp) lastp = tsfp;
			}
			
			packet_last = lastp;
			packet_last_first = firstp;
		
		}
		

		
		/* Create a global order */		
		Vector<DataPacket> dpv = new Vector<DataPacket>();
		for(NodeData nd : nodedata.values()){
			dpv.addAll(nd.packets);
		}
		Collections.sort(dpv, new Comparator<DataPacket>() {
			public int compare(DataPacket dp1, DataPacket dp2){
				if(dp1.ts > dp2.ts) return 1;
				if(dp1.ts < dp2.ts) return -1;
				if(dp1.src > dp2.src) return 1;
				if(dp1.src < dp2.src) return -1;
				if(dp1.id > dp2.id) return 1;
				if(dp1.id < dp2.id) return -1;
				return 0;
			   }
			});
		
		data = dpv.toArray(new DataPacket[0]);
		
		
		
		
	
	}
	
	
	public Integer getNormalizedTime(DataPacket dp){
		return normalized_time.get(dp);
	}
	
	public Integer[] getNodeIds(){
		return nodedata.keySet().toArray(new Integer[0]);
	}
	
	public NodeData getNodeData(int id){
		return nodedata.get(id);
		
	}
	
	public DataPacket[] getPackets(){
		return data.clone();
	}
	
	
	public String getStats(){
		StringBuilder rv = new StringBuilder();
		if(nodedata == null){
			rv.append("No Data available.\n");
			return rv.toString();
			
		}
		
		
		rv.append("\nGeneral stats\n=============\n");
		rv.append("\tSink Reboots:    " + sinkreboot + "\n");
		rv.append("Last first packet: " + packet_first_last + "\n" );
		rv.append("First last packet: " + packet_last_first + "\n" );
		rv.append("Last packet:       " + packet_last + "\n" );
		int t  = packet_last_first - packet_first_last;
		if (t > 0) {
			rv.append("Full dataset:      " + (t / (60*60))  + "h " + ((t / 60) % 60) +  "m " + (t % 60) + "s\n" );
		} else {
			t = -t;
			rv.append("Full dataset missing: " + (t / (60*60))  + "h " + ((t / 60) % 60) +  "m " + (t % 60) + "s\n" );
		}
		 
		
		rv.append("\nPacket stats\n============\n");
		for(NodeData nd : nodedata.values()){
			rv.append("Node:   " + RealSimUtil.idToStringInt(nd.id) + ":\n" );
			rv.append("\tPackts: " + nd.packets.size() + "\n");
			rv.append("\tDups  : " + nd.dups + "\n");
			rv.append("\tMoved : " + nd.moved + "\n");
			rv.append("\tLost  : " + nd.packets_lost + "\n");
			rv.append("\tOfsset: " + nd.offset_sink + "\n");
			rv.append("\n");
		
		}
		
		return rv.toString();
	}
	
}
