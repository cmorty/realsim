package de.fau.cooja.plugins.realsim;

import org.apache.log4j.Logger;

import se.sics.cooja.Mote;
import se.sics.cooja.MoteType;
import se.sics.cooja.Simulation;
import se.sics.cooja.interfaces.Radio;
import se.sics.cooja.radiomediums.DGRMDestinationRadio;
import se.sics.cooja.radiomediums.DirectedGraphMedium;
import se.sics.cooja.radiomediums.DirectedGraphMedium.Edge;

public class RealSim {
	private static Logger	logger			= Logger.getLogger(RealSim.class);
	Simulation sim;
	
	RealSim(Simulation simu){
		sim = simu;
	}
	
	public void clear(){
		while (sim.getMotesCount() > 0) {
			sim.removeMote(sim.getMote(0));
		}
	}
	
	
	
	
	public boolean addmote(Integer id, MoteType mt){
		
		
		logger.info("Adding mote: " + id);
		
		if (sim.getMoteWithID(id) != null) {
			return false;
		}
		
		
		Mote mote = mt.generateMote(sim);
		
		
		//Position at random place for a start
		// TODO use positioner?
		double x = (Math.random() * 10000) % 15;
		double y = (Math.random() * 10000) % 15;
		mote.getInterfaces().getPosition().setCoordinates(x, y, 0);
		mote.getInterfaces().getMoteID().setMoteID(id);
		
		
		//Add after everything is configured

		sim.addMote(mote);


		
		return true;
	}
	
	public boolean rmMote(Integer id){
		Mote rmm  = sim.getMoteWithID(id);
		DirectedGraphMedium rm = (DirectedGraphMedium) sim.getRadioMedium();
		
		if (rmm == null) {
			return false;
		}
		//remove edges
		for (DirectedGraphMedium.Edge e : rm.getEdges()) {
			Radio src = e.source;
			Radio dst = e.superDest.radio;
			if (src.getMote().getID() == id || dst.getMote().getID() == id) {
				rm.removeEdge(e);
			}
		}
		
		//remove mote
		sim.removeMote(rmm);
		return true;
	}
	
	
	private Edge rsEdge2Edge(RealSimEdge rse){
		DirectedGraphMedium rm = (DirectedGraphMedium) sim.getRadioMedium();
	
		for (DirectedGraphMedium.Edge e : rm.getEdges()) {
			int src = e.source.getMote().getID();
			int dst = e.superDest.radio.getMote().getID();
			if (src == rse.src && dst == rse.dst) {
				return e;
			}
		}
		return null;
	}
	
	
	public boolean rmEdge(RealSimEdge rse){
		DirectedGraphMedium rm = (DirectedGraphMedium) sim.getRadioMedium();
		
		Edge ed = rsEdge2Edge(rse);
		if(ed == null) return false;
		
		rm.removeEdge(ed);
		return true;
	}
	
	public void setEdge(int src, int dst, double ratio, double rssi, int lqi){
		RealSimEdge rse = new RealSimEdge(src, dst);
		rse.ratio = ratio;
		rse.rssi = rssi;
		rse.lqi = lqi;
		setEdge(rse);
	}
	
	
	public void setEdge(RealSimEdge rse){
		// Remove old existing edge
		logger.info("Setting edge: " + rse.src + " - " + rse.dst);
		DirectedGraphMedium rm = (DirectedGraphMedium) sim.getRadioMedium();
		DGRMDestinationRadio dr;
		
		Edge edge = rsEdge2Edge(rse);
		
		if(edge != null){
			dr = (DGRMDestinationRadio)edge.superDest;
		} else {
			dr = new DGRMDestinationRadio(sim.getMoteWithID(rse.dst).getInterfaces().getRadio());
		}
		
		dr.ratio = rse.ratio;
		dr.signal = (rse.rssi) - 100;
		dr.lqi = rse.lqi;
		
		if(edge == null){
			edge = new Edge(sim.getMoteWithID(rse.src).getInterfaces().getRadio(), dr);
			rm.addEdge(edge);
		}
		
		//TODO: Set delay
		
		rm.requestEdgeAnalysis(); //This just sets a flag as done by addEdge - No harm in doing it again

		
	}
	
}
