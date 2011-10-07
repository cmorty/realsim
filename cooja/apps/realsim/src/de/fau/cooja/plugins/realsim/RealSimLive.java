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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Observable;
import java.util.Observer;
import java.util.StringTokenizer;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JComboBox;

import se.sics.cooja.AddressMemory;
import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.Mote;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.MoteType;
import se.sics.cooja.VisPlugin;
import se.sics.cooja.interfaces.Radio;
import se.sics.cooja.plugins.Visualizer;
import se.sics.cooja.radiomediums.DGRMDestinationRadio;
import se.sics.cooja.radiomediums.DirectedGraphMedium;
import se.sics.cooja.radiomediums.DirectedGraphMedium.Edge;

@ClassDescription("RealSim Live")
@PluginType(PluginType.SIM_STANDARD_PLUGIN)
public class RealSimLive extends VisPlugin implements ActionListener, Observer {
	
	private static final long	serialVersionUID	= 4368807123350830772L;
	protected Simulation		sim;
	
	ServerSocket				serverSocket;
	public JPanel				controlPanel		= new JPanel();
	JToggleButton				set_port			= new JToggleButton("Click to start with port:");
	JTextField					insert_port			= new JTextField(4);
	JComboBox					default_node		= new JComboBox();
	
	public RealSimLive(Simulation simulation, GUI gui) {
		super("RealSim Live", gui);
		this.sim = simulation;
	}
	
	public void startPlugin() {
		//Do not start if we do not support the medium
		if(!(sim.getRadioMedium() instanceof DirectedGraphMedium)) return;
		insert_port.setToolTipText("PORT");
		add("Center", controlPanel);
		controlPanel.add(set_port);
		set_port.addActionListener(this);
		controlPanel.add(insert_port);
		insert_port.addActionListener(this);
		controlPanel.add(default_node);
		insert_port.addActionListener(this);
		sim.addObserver(this);
		System.out.println("Added observer");
		
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
				serverSocket = new ServerSocket(port);
				Listener l = new Listener(this);
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
	
	@Override
	/**
	 * TODO This might need some cleanup
	 */
	public void update(Observable obj, Object arg1) {
		int cnt, cnt2;
		MoteType[] mt = sim.getMoteTypes();
		int num = default_node.getItemCount();
		ArrayList<String> itms = new ArrayList<String>();
		
		for (cnt = 0; cnt < num; cnt++) {
			itms.add((String) default_node.getItemAt(cnt));
		}
		
		// Add missing
		for (cnt = 0; cnt < mt.length; cnt++) {
			System.out.println("IT" + mt[cnt].getIdentifier() + " - " + mt[cnt].getDescription());
			for (cnt2 = 0; cnt2 < itms.size(); cnt2++) {
				if (mt[cnt].getDescription().equals(itms.get(cnt2)))
					break;
			}
			if (cnt2 == itms.size()) {
				default_node.addItem(mt[cnt].getDescription());
			}
		}
		
	}
}

class Listener extends Thread {
	
	private Simulation			sim;
	private DirectedGraphMedium	radioMedium	= null;
	private ServerSocket		serverSocket;
	public Socket				socket;
	private JPanel				controlPanel;
	private JComboBox			default_node;
	private RealSim				rs;
	
	public Listener(RealSimLive rsl) throws IOException {
		this.sim = rsl.sim;
		this.serverSocket = rsl.serverSocket;
		this.radioMedium = (DirectedGraphMedium) sim.getRadioMedium();
		this.radioMedium.clearEdges();
		this.controlPanel = rsl.controlPanel;
		this.default_node = rsl.default_node;
		rs = new RealSim(this.sim);
	}
	
	public void run() {
		try {
			while (true) {
				Socket socket = serverSocket.accept();
				JTextField c = new JTextField();
				c.setText("Connected: " + socket.getInetAddress().getHostName());
				controlPanel.add(c);
				controlPanel.add(new JTextField());
				InputStream in = socket.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String line;
				HashMap<Integer, Integer> motes = new HashMap<Integer, Integer>();
				
				lines: while ((line = reader.readLine()) != null) {
					JTextField newline = (JTextField) controlPanel.getComponent(2);
					newline.setText(line);
					controlPanel.updateUI();
					if (!line.contains("::") || line.contains("MAC"))
						continue;
					StringTokenizer t = new StringTokenizer(line, "::");
					while (t.hasMoreElements()) {
						String token = t.nextToken();
						
						// Clear all Nodes
						if (token.equals("clear")) {
							rs.clear();
						}
						
						// Fill internal mote Array
						if (token.equals("node")) {
							while (t.hasMoreElements()) {
								String s = t.nextToken();
								if (s.length() > 2 && !s.equals("node")) {
									try {
										Integer id1 = new Integer(s.substring(0, s.indexOf('.')));
										Integer id2 = new Integer(s.substring(s.indexOf('.') + 1, s.length()));
										motes.put(id1, id2);
									} catch (NumberFormatException e) {
										continue lines;
									} catch (StringIndexOutOfBoundsException e) {
										continue lines;
									}
								}
							}
							
							// Check for mote removal (remove also from edges)
							for (Mote sim_mote : sim.getMotes()) {
								if (!motes.containsKey(sim_mote.getID())) {
									rs.rmMote(sim_mote.getID());
								}
							}
							
							for (Integer id : motes.keySet()) {
								// Check if Mote already added to Simulation
								MoteType moteT = null;
								
																
								MoteType[] mt = sim.getMoteTypes();
								
								// Find MoteType
								// TODO Make this configurable
								for (int cnt = 0; cnt < mt.length; cnt++) {
									moteT = mt[cnt];
									if (moteT.getDescription().equals(default_node.getSelectedItem().toString()))
										break;
									;
								}
								
								rs.addmote(id, moteT);
								
							
							}
						}
						
						// Add Edge to DGRMConfigurator
						if (token.equals("edge")) {
							try {
								String s1 = t.nextToken();
								String s2 = t.nextToken();
								int id_src;
								int id_dst;
								RealSimEdge edge;
								id_src = new Integer(s1.substring(0, s1.indexOf('.')));
								id_dst = new Integer(s2.substring(0, s2.indexOf('.')));
								edge = new RealSimEdge(id_src, id_dst);
								
								if (!getMotesID().contains(id_src) || !getMotesID().contains(id_dst)) {
									continue lines;
								}
								

								
								// Add new Edge
								edge.ratio = new Double(t.nextToken()) / 100.0;
								edge.rssi = new Integer(t.nextToken());
								edge.lqi = new Integer(t.nextToken());
								if (edge.ratio <= 0.0 || edge.ratio > 1.0 || 
										edge.rssi > 90 || edge.rssi <= 0 || 
										edge.lqi > 110 || edge.lqi <= 0) {
									//TODO Error
									continue lines;
								}
								
								rs.setEdge(edge);
								/*g.getPanel().addEdge(String.valueOf(id_src), 
										String.valueOf(id_dst), 
										(int) Math.pow(90 - rssi, 2) / 25,
										(int) ((Math.pow(110 - lqi, 2)) + (100 - (100 * ratio))));
								v.resetViewport++;*/
								
								// Ignore those Exceptions
							} catch (NumberFormatException e) {
								continue lines;
							} catch (NoSuchElementException e) {
								continue lines;
							} catch (StringIndexOutOfBoundsException e) {
								continue lines;
							}
						}
					}
					motes.clear();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 
	 * @return Mote by IDs
	 */
	public ArrayList<Integer> getMotesID() {
		ArrayList<Integer> ids = new ArrayList<Integer>();
		for (Mote m : sim.getMotes()) {
			ids.add(m.getID());
		}
		return ids;
	}
}
