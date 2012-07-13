/**
 * Copyright (c) 2011, Simon Böhm
 * Copyright (c) 2011, Moritz Strübe
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * @author Simon Böhm <mail@boehm-simon.de>
 * @author Moritz "Morty" Strübe <Moritz.Struebe@informatik.uni-erlangen.de>
 * 
 */

package de.fau.cooja.plugins.realsim;


import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Vector;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import org.apache.log4j.Logger;

import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.Mote;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;
import se.sics.cooja.radiomediums.DirectedGraphMedium;
import de.fau.realsim.Connection;
import de.fau.realsim.DataPacket;
import de.fau.realsim.DataPacketHandler;
import de.fau.realsim.RealSimClient;


@ClassDescription("RealSim Live")
@PluginType(PluginType.SIM_PLUGIN)
public class RealSimLive extends VisPlugin implements ActionListener, DataPacketHandler{
	
	private static final long	serialVersionUID	= 4368807123350830772L;
	protected Simulation		sim;
	
	private final static String failmsg = "This Plugin needs a DGRM.";
	
	public JPanel				controlPanel		= new JPanel();
	JToggleButton				set_port			= new JToggleButton("Click to start with port:");
	JTextField					insert_port			= new JTextField(4);
	JComboBox					default_node;
	
	private static Logger	logger			= Logger.getLogger(RealSimLive.class);

	
	RealSim rs = null;
	
	public RealSimLive(Simulation simulation, GUI gui) {
		super("RealSim Live", gui);
		this.sim = simulation;
		rs = new RealSim(sim, gui);
	}
	
	public void startPlugin() {
		//Do not start if we do not support the medium
		if (!(sim.getRadioMedium() instanceof DirectedGraphMedium)) {
			JOptionPane.showMessageDialog(this, failmsg, "Unsufficiant environment", JOptionPane.WARNING_MESSAGE);
			add(new JLabel(failmsg));
			
			return;
		}
		
		default_node = new JComboBox(new MoteTypeComboboxModel(sim));
		
		insert_port.setToolTipText("PORT");
		add("Center", controlPanel);
		controlPanel.add(set_port);
		set_port.addActionListener(this);
		controlPanel.add(insert_port);
		insert_port.addActionListener(this);
		controlPanel.add(default_node);
		insert_port.addActionListener(this);
		
		//System.out.println("Added observer");
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setSize(180, 190);
		this.setLocation(320, 0);
		this.setBackground(Color.WHITE);
	}
	
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == set_port) {
			try {
				int port = new Integer(insert_port.getText());
				RealSimClient rsl = new RealSimClient(this);
				rsl.port= port;
				Thread l = new Thread(rsl);
				l.start();
				controlPanel.removeAll();
				JProgressBar bar = new JProgressBar(JProgressBar.HORIZONTAL);
				bar.setValue(0);
				bar.setString("Listening... (" + port + ")");
				bar.setStringPainted(true);
				bar.setIndeterminate(true);
				controlPanel.add(bar);
				controlPanel.add(default_node);
				updateUI();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}
	


	


	private HashMap<Integer, DataPacket> dpm= new HashMap<Integer, DataPacket>(); 
	
	private class Edge{
		final int src;
		final int dst;
		
		public Edge(int src, int dst) {
			this.src = src;
			this.dst = dst;
		}
		
		
		@Override
		public int hashCode() {
			return src * 255* 255 + dst;
		}
		
		@Override
		public boolean equals(Object obj) {
			return (hashCode() == obj.hashCode());
		}
		
	}
	
	private HashMap<Edge, Long> edgetimes = new  HashMap<Edge, Long>();
	
	
	

	
	private String idOut(int id){
		Integer i = new Integer(id);
		return i.toString() + " (" + Integer.toHexString(id) + ")";
	}
	
	
	
	private void addmote(int id){
		MoteTypeComboboxModel mtbm = (MoteTypeComboboxModel) default_node.getModel();
		rs.addmote(id, mtbm.getSelectedMote());
	}
	
	public void testTimes(long hts){
		
		for(Entry<Edge, Long> entry : edgetimes.entrySet()){
			if(hts - entry.getValue() > 3 * 60){ //FIXME remove constant
				Edge e = entry.getKey();
				if(rs.rmEdge(e.src, e.dst)){
					logger.info("Removed: " + idOut(e.src) + " " + idOut(e.dst));
				}
			}
		}
		
		
	}
	
	public void handleDataPaket(DataPacket dp){
		
		//Add the mote
		addmote(dp.src);
		
		//Get all other motes
		Vector<Integer> mids = moteids();
		
		//Remove self.
		mids.removeElement(dp.src);
		//Add this datapacket
		dpm.put(dp.src, dp);
		
		for(Connection cn : dp.getCns()){
			if(mids.contains(cn.node)) { // Does the node already exist
				mids.removeElement(cn.node); //Remove node from possible Connections
			} else {
				addmote(cn.node); //Create node
			}
			if(cn.rcv != 0){ // Set edge
				rs.setEdge(cn.node, dp.src, (double)cn.rcv/(cn.rcv + cn.loss),  cn.rssi, cn.lqi);
				edgetimes.put(new Edge(cn.node, dp.src), dp.hts);
			}
		}

		testTimes(dp.hts);
			
	}
	
	


	private Vector<Integer> moteids(){
		Vector <Integer> rv = new Vector<Integer>();
		Mote[] mts = sim.getMotes();
		for(Mote m : mts){
			rv.add(m.getID());
		}
		return rv;
	}
}
