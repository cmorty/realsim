/**
 * Copyright (c) 2011, Simon Böhm
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
 * 
 */

/** Part of this code is derived from
 * http://java.sun.com/applets/jdk/1.4/demo/applets/GraphLayout/Graph.java
 * @(#)Graph.java	1.9 99/08/04
 *
 * Copyright (c) 1997, 1998 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Sun grants you ("Licensee") a non-exclusive, royalty free, license to use,
 * modify and redistribute this software in source and binary code form,
 * provided that i) this copyright notice and license appear on all copies of
 * the software; and ii) Licensee does not utilize the software in a manner
 * which is disparaging to Sun.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING ANY
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE
 * LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING
 * OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS
 * LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT,
 * INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF
 * OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES.
 *
 * This software is not designed or intended for use in on-line control of
 * aircraft, air traffic, aircraft navigation or aircraft communications; or in
 * the design, construction, operation or maintenance of any nuclear
 * facility. Licensee represents and warrants that it will not use or
 * redistribute the Software for such purposes.
 */

package de.fau.cooja.plugins.springlayout;

import java.util.*;
import java.util.List;

import java.awt.*;
import java.awt.event.*;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.apache.log4j.Logger;

import de.fau.cooja.plugins.realsim.RealSim;
import de.fau.cooja.plugins.realsim.RealSimFile;
import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.Mote;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;
import se.sics.cooja.plugins.*;
import se.sics.cooja.radiomediums.DirectedGraphMedium;
import se.sics.cooja.radiomediums.DGRMDestinationRadio;

class Node {
	int id;
	double	x;
	double	y;
	double	dx;
	double	dy;
	boolean	fixed;
	String	lbl;
}

class Edge {
	Node	src;
	Node	dst;
	Edge    co = null;
	double	rssi;
	double	lqi;
	boolean set = true;
	static double rssi_max = 300;
	static double lqi_max = 300;
	
	public double len(GraphPanel.Elength el) {
		switch(el){
			case RSSI:
				return rssi;
			case RSSI_max:
				double omax;
				omax = (co != null) ? co.rssi :  rssi_max;
				return Math.max(rssi, omax);
			case LQI:
				return lqi;
			case LQI_max:
				omax = (co != null) ? co.lqi :  lqi_max;
				return Math.max(lqi, omax);
				
		}
		throw new IllegalArgumentException();
	}
	
	public void setRSSI(double rssi) {
		this.rssi = rssi;
	}
	
	public void setLQI(double lqi) {
		this.lqi = lqi;
	}
	

	
	
}

class GraphPanel extends Panel implements Runnable, MouseListener, MouseMotionListener {
	private static Logger	logger			= Logger.getLogger(SpringLayout.class);
	enum Elength {
		RSSI,
		RSSI_max,
		LQI,
		LQI_max
	}
	
	
	private static final long	serialVersionUID	= 1L;
	public static Elength		view				= Elength.RSSI;
	public static double		scale				= 1.0;
	SpringLayout				graph;
	private ArrayList<Node>				nodes				= new ArrayList<Node>();
	private ArrayList<Edge>				edges				= new ArrayList<Edge>();
	Thread						relaxer;
	Simulation					sim;
	boolean changed = true;
	

	
	
	
	public GraphPanel(SpringLayout graph, Simulation sim) {
		this.graph = graph;
		this.sim = sim;
		addMouseListener(this);
	}
	
	
///////////////////
	
	protected Node getNode(int  id) {
		for (Node nd : nodes) {
			if(nd.id == id) {
				return nd;
			}
		}
		return null;
		//return addNode(id);
	}
	
	public synchronized Node addNode(int id) {
		logger.info("Addn: " + id);
		Node n;
		n = getNode(id);
		if(n != null) return n;
		
		n = new Node();
		n.x = 10 + 380 * Math.random();
		n.y = 10 + 380 * Math.random();
		n.id = id;
		n.lbl = ((Integer)id).toString();
		//Fix first node
		if (nodes.size() == 0) {
			n.fixed = true;
			n.x = graph.getWidth() / 2;
			n.y = graph.getHeight() / 2;
		}
		
		nodes.add(n);
		return n;
	}
	
	public synchronized void removeNode(int id){
		removeNode(getNode(id));
	}
	
	public synchronized void removeNode(Node n){
		
		//Get rid of the edges first.		
		for(Edge e : edges){
			if(e.src == n || e.dst == n){
				removeEdge(e);
			}
		}
		nodes.remove(n);
		
		
	}
	
	
	public synchronized void shake(){
		for (Node n : nodes) {
			if (!n.fixed) {
				n.x += 80 * Math.random() - 40;
				n.y += 80 * Math.random() - 40;
			}
		}
	}
	
	
///////////////////7	
	
	
	public Edge[] getEdges() {
		return  edges.toArray(new  Edge[0]);
	}
	
	
	public synchronized void resetEdges(){
		for(Edge e : edges){
			e.set = false;
		}
	}
	
	
	public synchronized void removeUnsetEdges(){
		@SuppressWarnings("unchecked")
		ArrayList<Edge> clone = (ArrayList<Edge>)edges.clone();
		for(Edge e : clone){ //Need to clone due to modification
			if(!e.set){
				removeEdge(e);
			}
		}
	}
	
	
	public synchronized void removeEdge(Edge e) {
		if(e.co != null){
			e.co.co = null;
		}
		logger.info("Remove " + e.toString());
		edges.remove(e);

	}
	
	public synchronized void setEdge(int src, int dst, double rssi, double lqi) {
		Edge e = null;
		Node snode, dnode;
		
		snode = getNode(src);
		dnode = getNode(dst);
		
		if(snode == null || dnode == null){
			logger.error("Failed to find source ("+src+") or dest-node ("+dst+").");
			return;
		}
		
		for(Edge te : edges){
			if(te.src == snode && te.dst == dnode){
				e = te;
			}
		}
	
		
		if(e == null){
			e = new Edge();
			e.src = snode;
			e.dst = dnode;			
			edges.add(e);
			Edge te = getEdge(dst, src);
			if(te != null){
				e.co = te;
				te.co = e;
			}
			
		}
		
		e.rssi = rssi;
		e.lqi = lqi;
		e.set = true;
		
	}
	
	protected synchronized Edge getEdge(int src, int dst){
		for(Edge ed : edges){
			if(ed.src.id == src && ed.dst.id == dst){
				return ed;
			}
		}
		
		return null;
	}
	

	
	public ArrayList<Node> getNodes() {
		@SuppressWarnings("unchecked")
		ArrayList<Node> clone = (ArrayList<Node>)  nodes.clone();
		return clone;
	}
	
	///////////////////////////////////////////////////////////////////
	
	public void run() {
		Thread me = Thread.currentThread();
		while (relaxer == me) {
			relax();
			repaint();
			try {
				for (Node n : nodes) {
					if (n != null && sim.getMoteWithID(n.id) != null) {
						sim.getMoteWithID(n.id).getInterfaces().getPosition().setCoordinates(n.x / 50, n.y / 50, 0);
					}
				}
			} catch (ConcurrentModificationException e) {
				
			}
			
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				break;
			}
		}
	}
	
	synchronized void relax() {
		for (int i = 0; i < edges.size(); i++) {
			Edge e = edges.get(i);
			double vx = e.dst.x - e.src.x;
			double vy = e.dst.y - e.src.y;
			double len = Math.sqrt(vx * vx + vy * vy);
			len = (len == 0) ? .0001 : len;
			double f = (edges.get(i).len(view) - len) / (len * 3);
			double dx = f * vx;
			double dy = f * vy;
			//I-Regler
			e.dst.dx += dx;
			e.dst.dy += dy;
			e.src.dx += -dx;
			e.src.dy += -dy;
		}
		
		for (int i = 0; i < nodes.size(); i++) {
			Node n1 = nodes.get(i);
			double dx = 0;
			double dy = 0;
			
			for (int j = 0; j < nodes.size(); j++) {
				if (i == j) {
					continue;
				}
				Node n2 = nodes.get(j);
				double vx = n1.x - n2.x;
				double vy = n1.y - n2.y;
				double len = vx * vx + vy * vy;
				if (len == 0) {
					dx += Math.random();
					dy += Math.random();
				} else if (len < 100 * 100) {
					dx += vx / len;
					dy += vy / len;
				}
			}
			double dlen = dx * dx + dy * dy;
			if (dlen > 0) {
				dlen = Math.sqrt(dlen) / 2;
				n1.dx += dx / dlen;
				n1.dy += dy / dlen;
			}
		}
		
		Dimension d = getSize();
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			if (!n.fixed) {
				if (Math.abs(Math.max(-10, Math.min(10, n.dx))) > 0.5) {
					n.x += Math.max(-10, Math.min(10, n.dx));
				}
				if (Math.abs(Math.max(-10, Math.min(10, n.dy))) > 0.5) {
					n.y += Math.max(-10, Math.min(10, n.dy));
				}
			}
			if (n.x < 0) {
				n.x = 0;
			} else if (n.x > d.width) {
				n.x = d.width;
			}
			if (n.y < 0) {
				n.y = 0;
			} else if (n.y > d.height) {
				n.y = d.height;
			}
			
			n.dx /= 2;
			n.dy /= 2;
		}
		
	}
	
	Node		pick;
	boolean		pickfixed;
	Image		offscreen;
	Dimension	offscreensize;
	Graphics	offgraphics;
	
	final Color	fixedColor	= Color.red;
	final Color	selectColor	= Color.red;
	final Color	edgeColor	= Color.black;
	final Color	nodeColor	= Color.white;
	final Color	arcColor1	= Color.black;
	final Color	arcColor2	= Color.green;
	final Color	arcColor3	= Color.red;
	
	public void paintNode(Graphics g, Node n, FontMetrics fm) {
		int x = (int) n.x;
		int y = (int) n.y;
		g.setColor((n == pick) ? selectColor : (n.fixed ? fixedColor : nodeColor));
		int w = fm.stringWidth(n.lbl) + 10;
		int h = fm.getHeight() + 4;
		g.fillRect(x - w / 2, y - h / 2, w, h);
		g.setColor(Color.black);
		g.drawRect(x - w / 2, y - h / 2, w - 1, h - 1);
		g.drawString(n.lbl, x - (w - 10) / 2, (y - (h - 4) / 2) + fm.getAscent());
	}
	
	public synchronized void update(Graphics g) {
		Dimension d = getSize();
		if ((offscreen == null) || (d.width != offscreensize.width) || (d.height != offscreensize.height)) {
			offscreen = createImage(d.width, d.height);
			offscreensize = d;
			if (offgraphics != null) {
				offgraphics.dispose();
			}
			offgraphics = offscreen.getGraphics();
			offgraphics.setFont(getFont());
		}
		
		offgraphics.setColor(getBackground());
		offgraphics.fillRect(0, 0, d.width, d.height);
		for (int i = 0; i < edges.size(); i++) {
			Edge e = edges.get(i);
			int x1 = (int) e.src.x;
			int y1 = (int) e.src.y;
			int x2 = (int) e.dst.x;
			int y2 = (int) e.dst.y;
			int len = (int) Math.abs(Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)) - e.len(view));
			offgraphics.setColor((len < 10) ? arcColor1 : (len < 20 ? arcColor2 : arcColor3));
			offgraphics.drawLine(x1, y1, x2, y2);
			offgraphics.drawString(String.valueOf(e.len(view)), x1 + (x2 - x1) / 2, y1 + (y2 - y1) / 2);
			offgraphics.setColor(edgeColor);
			//repaint();
		}
		
		FontMetrics fm = offgraphics.getFontMetrics();
		for (int i = 0; i < nodes.size(); i++) {
			paintNode(offgraphics, nodes.get(i), fm);
		}
		//System.out.println("Update");
		g.drawImage(offscreen, 0, 0, null);
	}
	
	// 1.1 event handling
	public void mouseClicked(MouseEvent e) {
	}
	
	public void mousePressed(MouseEvent e) {
		addMouseMotionListener(this);
		double bestdist = Double.MAX_VALUE;
		int x = e.getX();
		int y = e.getY();
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			double dist = (n.x - x) * (n.x - x) + (n.y - y) * (n.y - y);
			if (dist < bestdist) {
				pick = n;
				bestdist = dist;
			}
		}
		if (pick == null) {
			repaint();
			e.consume();
			return;
		}
		pickfixed = pick.fixed;
		pick.fixed = true;
		pick.x = x;
		pick.y = y;
		repaint();
		e.consume();
	}
	
	public void mouseReleased(MouseEvent e) {
		removeMouseMotionListener(this);
		if (pick != null) {
			pick.x = e.getX();
			pick.y = e.getY();
			pick.fixed = pickfixed;
			pick = null;
		}
		repaint();
		e.consume();
	}
	
	public void mouseEntered(MouseEvent e) {
	}
	
	public void mouseExited(MouseEvent e) {
	}
	
	public void mouseDragged(MouseEvent e) {
		if (pick != null) {
			pick.x = e.getX();
			pick.y = e.getY();
			repaint();
			e.consume();
		}
	}
	
	public void mouseMoved(MouseEvent e) {
	}
	
	public void start() {
		relaxer = new Thread(this);
		relaxer.start();
	}
	
	
	public void stop() {		
		relaxer = null;
	}
	

	
}

@ClassDescription("Spring Layout")
@PluginType(PluginType.SIM_STANDARD_PLUGIN)
public class SpringLayout extends VisPlugin implements ActionListener, ItemListener, Observer {
	private static Logger	logger			= Logger.getLogger(SpringLayout.class);
	private static final long	serialVersionUID	= 1L;
	
	GraphPanel					panel;
	JPanel						controlPanel;
	Simulation					sim;
	DirectedGraphMedium			radioMedium			= null;
	
	Button						clear				= new Button("Clear");
	Button						shake				= new Button("Shake");
	ComboBoxItem [] comboBoxItems = {new ComboBoxItem("RSSI", GraphPanel.Elength.RSSI),
			new ComboBoxItem("RSSI (Max)", GraphPanel.Elength.RSSI_max),
			new ComboBoxItem("LQI", GraphPanel.Elength.LQI),
			new ComboBoxItem("LQI (Max)", GraphPanel.Elength.LQI_max)};
	
	JComboBox						layout = new JComboBox(comboBoxItems);
	Button						zoom_in				= new Button("+");
	Button						zoom_out			= new Button("-");
	
	JToggleButton				pause				= new JToggleButton("Pause");
	JToggleButton				max_min				= new JToggleButton("Avrg View");
	
	class ComboBoxItem {
		
		String				label;
		GraphPanel.Elength	key;
		
		ComboBoxItem(String l, GraphPanel.Elength k) {
			label = l;
			key = k;
		}
		
		public String toString() {
			return label;
		}
		
		public GraphPanel.Elength getKey() {
			return key;
		}
	}
	
	int edgeHash;
	int moteHash;
	
	
	
	public SpringLayout(Simulation sim, GUI gui) {
		super("SpringLayout", gui);
		this.sim = sim;
		this.radioMedium = (DirectedGraphMedium) sim.getRadioMedium();
		this.init();
		this.start();
	}
	
	public void init() {
		panel = new GraphPanel(this, this.sim);
		add("Center", panel);
		controlPanel = new JPanel();
		add("South", controlPanel);
		controlPanel.add(clear);
		clear.addActionListener(this);
		controlPanel.add(shake);
		shake.addActionListener(this);
		controlPanel.add(layout);
		layout.addActionListener(this);

		controlPanel.add(zoom_in);
		zoom_in.addActionListener(this);
		controlPanel.add(zoom_out);
		zoom_out.addActionListener(this);
		controlPanel.add(pause);
		pause.addActionListener(this);
		controlPanel.add(max_min);
		max_min.addActionListener(this);
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocation(510, 0);
		this.setSize(520, 320);
		this.setBackground(Color.WHITE);
		updateUI();
		repaint();
		
		//Update Motes first!

		updateMotes();
		updateEdges();
		//Register observers
		radioMedium.addRadioMediumObserver(this);
		sim.addObserver(this);

		
		
	}
	
	public void destroy() {
		remove(panel);
		remove(controlPanel);
	}
	
	public void start() {
		panel.start();
	}
	
	public void stop() {
		panel.stop();
	}
	
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == clear) {
			//panel.nodes.clear();
			//panel.edges.clear();
			return;
		}
		if (src == shake) {
			
			panel.shake();
		}
		if (src == pause) {
			if (!pause.getModel().isSelected()) {
				panel.start();
				repaint();
			} else {
				panel.stop();
				repaint();
			}
		}
		if(src == layout){
			GraphPanel.view = ((ComboBoxItem) layout.getSelectedItem()).getKey();
		}

		if (src == zoom_in) {
			GraphPanel.scale += 0.1;
			repaint();
		}
		if (src == zoom_out) {
			if (GraphPanel.scale > 0.1) {
				GraphPanel.scale -= 0.1;
			}
			repaint();
		}
	}
	
	public GraphPanel getPanel() {
		return this.panel;
	}
	
	public void itemStateChanged(ItemEvent e) {
		
	}
	
	void updateEdges(){
		logger.info("updating edges");
		panel.resetEdges();
		for(DirectedGraphMedium.Edge e : radioMedium.getEdges()){
			int src = e.source.getMote().getID();
			int dst = e.superDest.radio.getMote().getID();
			double rssi = ((DGRMDestinationRadio)e.superDest).signal;
			double lqi = ((DGRMDestinationRadio)e.superDest).lqi;
			logger.info("Edge " + src + " " + dst);
			panel.setEdge(src, dst, rssi, lqi);
			
		}
		panel.removeUnsetEdges();
	}
	
	void updateMotes(){
		logger.info("updating motes");
		ArrayList<Node> nds = panel.getNodes();
		
		for(Mote m : sim.getMotes()){
			Node nd;
			nd = panel.getNode(m.getID());
			if(nd == null){ //Mote does not exist -> Add
				panel.addNode(m.getID());
			} else { // Mote exists -> Do net remove it later
				nds.remove(nd);
			}
			
		}
		
		for(Node n : nds){
			panel.removeNode(n);
		}
	}

	@Override
	public void update(Observable o, Object arg) {
		
		if(o == radioMedium.getRadioMediumObservable()){
			updateEdges();
		}
		if(o == sim){
			updateMotes();
			
		}
		
		
	}
}

