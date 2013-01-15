package de.fau.realsim;

import java.io.Serializable;
import java.util.ArrayList;


public class DataPacket implements Serializable{
	
	
	private static final long serialVersionUID  = -6964661634166716077L;
	
	/** Gateway of global timestamp */
	public final int ts; 
	/** Source id */
	public final int src;
	/** Packet Id */
	public final int id;
	/** Source Timestamp */
	public final int sts;
	/** Configred intercal */
	public final int interv;
	/** Host Timestamp */
	public final long hts;
	
	
	
	private ArrayList<Connection> cns = new ArrayList<Connection>();
	
	public Connection[] getCns() {
		return cns.toArray(new Connection[cns.size()]);
	}


	public DataPacket(int ts_, int src_, int id_, int sts_, int interv_, long hts_){
		ts = ts_;
		src = src_;
		id = id_;
		sts = sts_;
		interv = interv_;
		hts = hts_ ;
	}
	
	public DataPacket(DataPacket odp, int offset){
		ts = odp.ts;
		src = odp.src;
		id = odp.id;
		sts = odp.sts + offset;
		interv = odp.interv;
		hts=odp.hts;
	}
	
	
	public void addConn(Connection cnn){
		cns.add(cnn);
	}
	
	public void trim(){
		cns.trimToSize();
	}
	
	public boolean same (DataPacket dp) {
		
		//Check basic data first
	    if(		src != dp.src ||
	    		id != dp.id ||
	    		sts != dp.sts ||
	    		interv != dp.interv
	    		) {
	    	return false;
	    }
	    
    	//Check connections
    	
    	Connection[] ocns = dp.getCns();
    		
    	if(cns.size() != ocns.length){
    		return false;
    	}
    	
    	for(Connection cn : ocns){
    		if(!cns.contains(cn)){
    			return false;
    		}
    	}
    	return true;
	    
	    
	}
	
	public int compare(DataPacket dp){
		if(ts > dp.ts) return 1;
		if(ts < dp.ts) return -1;
		if(src > dp.src) return 1;
		if(src < dp.src) return -1;
		if(id > dp.id) return 1;
		if(id < dp.id) return -1;
		
		
		return 0;
	}
	
}
