package de.fau.realsim;

import java.io.Serializable;

public class Connection implements Serializable {
	public final int node, rssi, lqi, rcv, loss, dup;
	private static final long serialVersionUID  = 7504555268854832873L;
	
	public Connection(int node_, int rssi_, int lqi_, int rcv_, int loss_, int dup_) {
		node = node_;
		rssi = rssi_;
		lqi = lqi_;
		rcv = rcv_;
		loss = loss_;
		dup = dup_;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if (!(other instanceof Connection)) {
			return false;
		}
		Connection o = (Connection) other;
		if (o.node == node && o.rssi == rssi && o.lqi == lqi && o.rcv == rcv && o.loss == loss && o.dup == dup) {
			return true;
		}
		return false;	
	}
	
	
	
	
	public int hashCode() {
		return (node << 16) + (rssi << 12) + (lqi << 4) + rcv + loss + dup ;
	}
	
}
