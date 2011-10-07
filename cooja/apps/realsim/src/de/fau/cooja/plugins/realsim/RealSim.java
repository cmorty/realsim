package de.fau.cooja.plugins.realsim;

import se.sics.cooja.AddressMemory;
import se.sics.cooja.Mote;
import se.sics.cooja.MoteType;
import se.sics.cooja.Simulation;
import se.sics.cooja.interfaces.Radio;
import se.sics.cooja.radiomediums.DGRMDestinationRadio;
import se.sics.cooja.radiomediums.DirectedGraphMedium;
import se.sics.cooja.radiomediums.DirectedGraphMedium.Edge;

public class RealSim {
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
		boolean isRunning = sim.isRunning();
		
		
		if (sim.getMoteWithID(id) != null) {
			return false;
		}
		
		
		if (isRunning) {
			sim.stopSimulation();
		}
		
		Mote mote = mt.generateMote(sim);
		sim.addMote(mote);
		
		//Position at random place for a start
		// TODO use positioner?
		double x = (Math.random() * 10000) % 15;
		double y = (Math.random() * 10000) % 15;
		mote.getInterfaces().getPosition().setCoordinates(x, y, 0);
		mote.getInterfaces().getMoteID().setMoteID(id);

		// TODO: Add observer
		//g.getPanel().addNode(String.valueOf(id));
		

		
		// Set Rimeaddress
		byte[] rime_addr = new byte[2];
		rime_addr[0] = id.byteValue();
		rime_addr[1] = ((Integer)(id/255)).byteValue(); //TODO this can certainly be done nicer
		
		((AddressMemory) mote.getMemory()).setByteArray("rimeaddr_node_addr", rime_addr);
		
		
		if (isRunning) {
			sim.startSimulation();
		}
		
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
	
	
	public boolean rmEdge(RealSimEdge rse){
		DirectedGraphMedium rm = (DirectedGraphMedium) sim.getRadioMedium();

		for (DirectedGraphMedium.Edge e : rm.getEdges()) {
			int src = e.source.getMote().getID();
			int dst = e.superDest.radio.getMote().getID();
			if (src == rse.src && dst == rse.dst) {
				rm.removeEdge(e);
				return true;
			}
		}
		return false;
	}
	
	public void setEdge(RealSimEdge rse){
		// Remove old existing edge
		boolean isRunning = sim.isRunning();
		if (isRunning) {
			sim.stopSimulation();
		}
		
		
		DirectedGraphMedium rm = (DirectedGraphMedium) sim.getRadioMedium();

		rmEdge(rse); //We don't care whether the edge existed before		
		
		
		DGRMDestinationRadio dr = new DGRMDestinationRadio(sim.getMoteWithID(rse.dst).getInterfaces().getRadio());
		dr.ratio = rse.ratio;
		dr.signal = (rse.rssi) - 100;
		dr.lqi = rse.lqi;
		DirectedGraphMedium.Edge newEdge = new Edge(sim.getMoteWithID(rse.src).getInterfaces().getRadio(), dr);
		rm.addEdge(newEdge);
		
		
		
		if (isRunning) {
			sim.startSimulation();
		}
		
		
	}
	
}
