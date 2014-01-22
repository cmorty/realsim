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
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map.Entry;

import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import org.apache.log4j.Logger;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.SupportedArguments;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.radiomediums.DirectedGraphMedium;






@ClassDescription("RealSim Live")
@PluginType(PluginType.SIM_PLUGIN)
@SupportedArguments(radioMediums = {DirectedGraphMedium.class})
public class RealSimLive extends VisPlugin implements ActionListener {
	
	private static Logger	logger			= Logger.getLogger(RealSimLive.class);
	int sd = 0;
	
	Timer tmr = new Timer(true); 
	
	private class Edge{
		final int src;
		final int dst;
		
		public Edge(int src, int dst) {	this.src = src;	this.dst = dst;	}
				
		@Override
		public int hashCode() {	return src * 255* 255 + dst;}
		
		@Override
		public boolean equals(Object obj) { return (hashCode() == obj.hashCode());	}
		
	}
	
	private ConcurrentHashMap<Edge, Integer> edgetimes = new  ConcurrentHashMap<Edge, Integer>();
	
	
	private class RealSimServer extends Thread{
		
		
		
		private void addmote(int id){
			MoteTypeComboboxModel mtbm = (MoteTypeComboboxModel) default_node.getModel();
			rs.addmote(id, mtbm.getSelectedMote());
		}
		
		
		Integer port;
		ServerSocket ls;
		Socket s;
		@Override
		public void run() {
			cstate(false);
			// TODO Auto-generated method stub
			logger.info("Starting logger on port " + port.toString());
			try {
				 ls= new ServerSocket(port);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				logger.error("Could not open port " + port);
				return;
			}
			logger.info("Waiting for connection on " + new Integer(ls.getLocalPort()).toString());
			while (true) {
				if( sd != 0 ) break; 
				
				BufferedReader reader;
				try {
					s = ls.accept();
					cstate(true);
					reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
					
				} catch (IOException e) {
					cstate(false);
					if(sd == 0){
						logger.error("Something went wrong while accepting connections.\n" + e.getMessage());
					}
					break;
				}
				logger.debug("Connected: " + s.getInetAddress().getHostName());
				String line;
				try {
					
					while ((line = reader.readLine()) != null) {
																	
						String[] el = line.split(" ");			
						if(el.length != 6){
							logger.warn("Unexpected number of elements: " + line);
							continue;
						}
						int timeout = 0, src = 0, dst = 0 ,prr = 0, rssi = 0, lqi = 0;
						Boolean ok = true;
						try {								
							 timeout = Integer.parseInt(el[0]); //In sec
							 src = Integer.parseInt(el[1]);	
							 dst = Integer.parseInt(el[2]);
							 prr = Integer.parseInt(el[3]);
							 rssi = Integer.parseInt(el[4]);
							 lqi = Integer.parseInt(el[5]);
							
							 
						}catch(Exception e){
							ok = false;
							logger.warn("Could not parse: " + line);
						}
						if(ok){
							if(!rs.moteExists(src)) addmote(src);
							if(!rs.moteExists(dst)) addmote(dst);
						
							rs.setEdge(src, dst, ((float)prr) / ((float)100),  rssi, lqi);
							edgetimes.put(new Edge(src, dst), timeout);
						}
						
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				cstate(false);
				logger.info("Connection closed");
				
			}
			//Close socket
			try {
				cstate(false);
				ls.close();
			} catch (IOException e) {
				//No need to catch this.
			}
		}
	}
	
	
	
	
	
	
	
	private static final long	serialVersionUID	= 4368807123350830772L;
	protected Simulation		sim;
	
	public JPanel				controlPanel		= new JPanel();
	JToggleButton				set_port			= new JToggleButton("Click to start with port:");
	JTextField					insert_port			= new JTextField(4);
	JComboBox					default_node;
	
	JLabel lab = new JLabel("");
	RealSimServer rss ;
	
	RealSim rs = null;
	
	public RealSimLive(Simulation simulation, Cooja cooja) {
		super("RealSim Live", cooja);
		this.sim = simulation;
		rs = new RealSim(sim, cooja);
	}
	
	public void startPlugin() {
		
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
		
		TimerTask decay = new TimerTask() {
			public void run() {
				
				Set<Entry<Edge, Integer>> es = edgetimes.entrySet();
				for(Entry<Edge, Integer> ent :  es){
					int time = ent.getValue();
					if(time > 0){
						if(time == 1)	es.remove(ent);
						else ent.setValue(time - 1);
					}
				}
			}
		};
		tmr.schedule(decay, 1000, 1000);
		
	}
	
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == set_port) {
			try {
				int port = new Integer(insert_port.getText());
				rss= new RealSimServer();
				rss.port= port;
				Thread l = new Thread(rss);
				l.start();
				controlPanel.removeAll();
				controlPanel.add(lab);
				controlPanel.add(default_node);
				updateUI();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	
	private void cstate(Boolean cnn) {
	
		if(!cnn){
			lab.setText("Listening... (" + rss.port + ")");
		} else {
			lab.setText("Connected");
		}
			
		
		
	}
	



	
}
