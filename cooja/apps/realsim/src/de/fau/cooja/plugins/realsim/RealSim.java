package de.fau.cooja.plugins.realsim;

import se.sics.cooja.AddressMemory;
import se.sics.cooja.Mote;
import se.sics.cooja.MoteType;
import se.sics.cooja.Simulation;

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
		
		if (isRunning) {
			sim.startSimulation();
		}
		
		// Set Rimeaddress
		byte[] rime_addr = new byte[2];
		rime_addr[0] = id.byteValue();
		rime_addr[1] = ((Integer)(id/255)).byteValue(); //TODO this can certainly be done nicer
		
		((AddressMemory) mote.getMemory()).setByteArray("rimeaddr_node_addr", rime_addr);
		
		return true;
	}
	
	public void addnode(){
		
	}
	
}
