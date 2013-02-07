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


import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import org.apache.log4j.Logger;

import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.Mote;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;
import se.sics.cooja.radiomediums.DGRMDestinationRadio;
import se.sics.cooja.radiomediums.DirectedGraphMedium;

class Node {
	int		id;
	double	x;
	double	y;
	double	dx;
	double	dy;
	boolean	fixed;
	String	lbl;
}

class Edge {
	Node			src;
	Node			dst;
	Edge			co			= null;
	double			rssi;
	double			lqi;
	boolean			set			= true;
	static double	rssi_max	= 300;
	static double	lqi_max		= 300;
	
	public double len(GraphPanel.Elength el) {
		switch (el) {
			case RSSI:
				return -rssi;
			case RSSI_max:
				double omax;
				omax = (co != null) ? co.rssi : rssi_max;
				return -Math.min(rssi, omax);
			case LQI:
				return lqi;
			case LQI_max:
				omax = (co != null) ? co.lqi : lqi_max;
				return Math.max(lqi, omax);
				
		}
		throw new IllegalArgumentException();
	}
	
	public String text(GraphPanel.Elength el) {
		String rv = null;
		switch (el) {
			case RSSI:
			case RSSI_max:
				rv = new Integer((int) rssi).toString() + " / ";
				rv += (co != null) ? new Integer((int) co.rssi).toString() : "None";
			case LQI:
			case LQI_max:
				rv = new Integer((int) lqi).toString() + " / ";
				rv += (co != null) ? new Integer((int) co.lqi).toString() : "None";
				
		}
		
		if(rv != null) return rv;
		throw new IllegalArgumentException();
	}
	
	public void setRSSI(double rssi) {
		this.rssi = rssi;
	}
	
	public void setLQI(double lqi) {
		this.lqi = lqi;
	}
	
	public Pair<Integer, Integer> getPair(){
		return new Pair<Integer, Integer>(src.id, dst.id);
	}
	
}

class Pair<L,R> {

	  private final L left;
	  private final R right;

	  public Pair(L left, R right) {
	    this.left = left;
	    this.right = right;
	  }

	  public L getLeft() { return left; }
	  public R getRight() { return right; }

	  @Override
	  public int hashCode() { return left.hashCode() ^ right.hashCode(); }

	  @Override
	  public boolean equals(Object o) {
	    if (o == null) return false;
	    if (!(o instanceof Pair)) return false;
	    @SuppressWarnings("unchecked")
		Pair<L, R> pairo = (Pair<L, R>) o;
	    return this.left.equals(pairo.getLeft()) &&
	           this.right.equals(pairo.getRight());
	  }

}

class GraphPanel extends JPanel implements Runnable, MouseListener, MouseMotionListener {
	private static Logger	logger	= Logger.getLogger(SpringLayout.class);
	
	enum Elength {
		RSSI, RSSI_max, LQI, LQI_max
	}
	
	private static final long	serialVersionUID	= 1L;
	public static Elength		view				= Elength.RSSI;
	public static int			scale				= 10;
	SpringLayout				graph;
	private Map<Integer, Node>	nodes				 = new  ConcurrentHashMap<Integer, Node>();
	private Map<Pair<Integer, Integer> , Edge> edges = new ConcurrentHashMap<Pair<Integer, Integer> , Edge>();
	Thread						relaxer;
	Simulation					sim;
	boolean						changed				= true;
	Image                       offscreen;
	Node						pick;
	boolean						pickfixed;
	
	Dimension					offscreensize;
	
	Semaphore					paintSem = new Semaphore(1);
	
	public GraphPanel(SpringLayout graph, Simulation sim) {
		this.graph = graph;
		this.sim = sim;
		addMouseListener(this);
		offscreensize = getSize();
		offscreen = createImage(offscreensize.width, offscreensize.height);
	}
	
	// /////////////////
	
	
	public Node addNode(Integer id) {
		
		Node n;
		n = nodes.get(id);
		if (n != null)	return n;
		
		n = new Node();
		n.x = 10 + 380 * Math.random();
		n.y = 10 + 380 * Math.random();
		n.id = id;
		n.lbl = ((Integer) id).toString();
		// Fix first node
		if (nodes.size() == 0) {
			n.fixed = true;
			n.x = graph.getWidth() / 2;
			n.y = graph.getHeight() / 2;
		}
		
		nodes.put(new Integer(id), n);
		return n;
	}
	
	public void removeNode(Integer id) {
		logger.info("Removing Node: " +id);
		nodes.remove(id);
		
	}

	
	
	
	
	public void shake() {
		for (Node n : nodes.values()) {
			if (!n.fixed) {
				n.x += 80 * Math.random() - 40;
				n.y += 80 * Math.random() - 40;
			}
		}
	}
	
	// /////////////////7
	
	public Edge[] getEdges() {
		return edges.values().toArray(new Edge[0]);
	}
	
	public  void resetEdges() {
		for (Edge e : edges.values()) {
			e.set = false;
		}
	}
	
	public  void removeUnsetEdges() {
		for (Edge e : edges.values()) { // Need to clone due to modification
			if (!e.set) {
				edges.remove(e.getPair());
			}
		}
	}
	
	public void removeEdge(Edge e) {
		if (e.co != null) {
			e.co.co = null;
		}
		logger.info("Remove " + e.toString());
		edges.remove(e.getPair());
		
	}
	
	
	
	Pair<Integer, Integer> getPair(int a, int b){
		return new Pair<Integer, Integer>(new Integer(a), new Integer(b));
	}
	
	public void setEdge(Integer src, Integer dst, double rssi, double lqi) {
		
		Node snode, dnode;
		
		snode = nodes.get(src);
		dnode = nodes.get(dst);
		
		if (snode == null || dnode == null) {
			String s = "";
			for(Integer i: nodes.keySet()) s = s + " " + i.toString() ;
			
			logger.error("Failed to find source (" + src + ") or dest-node (" + dst + ") - got:"  + s);
			
			return;
		}
		
		Pair<Integer, Integer> eKey = getPair(src, dst);
		Edge e = edges.get(eKey);
		
		
		if (e == null) {
			e = new Edge();
			e.src = snode;
			e.dst = dnode;
			edges.put(eKey, e);
			Edge te = edges.get(getPair(dst, src));
			if (te != null) {
				e.co = te;
				te.co = e;
			}
			
		}
		
		e.rssi = rssi;
		e.lqi = lqi;
		e.set = true;
		
	}
	
	
	public Collection<Node> getNodes() {
		return nodes.values();
	}
	
	public Set<Integer> getNodeIds() {
		return nodes.keySet();
	}
	
	// /////////////////////////////////////////////////////////////////
	
	public void run() {
		Thread me = Thread.currentThread();
		while (relaxer == me) {
			relax();
			paintImage();
			repaint();
			try {
				for (Node n : nodes.values()) {
					Mote m = sim.getMoteWithID(n.id);
					if (m != null) {
						m.getInterfaces().getPosition().setCoordinates(n.x / 50, n.y / 50, 0);
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
		for (Edge e : edges.values()) {
			double vx = e.dst.x - e.src.x;
			double vy = e.dst.y - e.src.y;
			double len = Math.sqrt(vx * vx + vy * vy);
			len = (len == 0) ? .0001 : len;
			double f = (e.len(view) * scale / 10  - len) / (len * 3);
			double dx = f * vx;
			double dy = f * vy;
			// I-Regler
			e.dst.dx += dx;
			e.dst.dy += dy;
			e.src.dx += -dx;
			e.src.dy += -dy;
		}
		
		for (Node n1 : nodes.values()) {
			double dx = 0;
			double dy = 0;
			
			for (Node n2 : nodes.values()) {
				if (n1 == n2) {
					continue;
				}
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
		for (Node n : nodes.values()) {
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
	
	
	void paintImage(){
		
		Dimension d = getSize();
		if(offscreen == null || offscreensize.width != d.width || offscreensize.height != d.height ){
			if(offscreen != null) offscreen.getGraphics().dispose();
					
		}
		if(d.width < 1 || d.height < 1 ) return;
		Image tmp = createImage(d.width, d.height);
		if(tmp == null) return;
		
		Graphics offgraphics = tmp.getGraphics();
		offgraphics.setFont(getFont());
		
		offgraphics.setColor(getBackground());
		offgraphics.fillRect(0, 0, d.width, d.height);
		
		for (Edge e : edges.values()) {
			int x1 = (int) e.src.x;
			int y1 = (int) e.src.y;
			int x2 = (int) e.dst.x;
			int y2 = (int) e.dst.y;
			if(e.co != null && x1 > x2 || (x1== x2 && y1 > y2)) continue;
			
			
			int len = (int) Math.abs(Math.sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)) - e.len(view) * scale / 10);
			offgraphics.setColor((len < 10) ? arcColor1 : (len < 20 ? arcColor2 : arcColor3));
			offgraphics.drawLine(x1, y1, x2, y2);
			offgraphics.drawString(String.valueOf(e.text(view)), x1 + (x2 - x1) / 2, y1 + (y2 - y1) / 2);
			offgraphics.setColor(edgeColor);
		}
		
		FontMetrics fm = offgraphics.getFontMetrics();
		for (Node n : nodes.values()) {
			paintNode(offgraphics, n, fm);
		}
		offscreen = tmp;
		offscreensize = d;
		//if(oldgraph != null) oldgraph.dispose();
		// System.out.println("Update");

	}	
/*
	@Override
	public void update(Graphics g) {
		super.update(g);
		Dimension d = getSize();
		if ((d.width != offscreensize.width) || (d.height != offscreensize.height)) paintImage();
		if(offscreen != null) 	g.drawImage(offscreen, 0, 0, null);
	
	}*/
	
	@Override
	public void  paintComponent( Graphics g ){
		
		//Dimension d = getSize();
		//if ((d.width != offscreensize.width) || (d.height != offscreensize.height)) paintImage();
		 ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		if(offscreen != null) 	g.drawImage(offscreen, 0, 0, null);

	}
	
	
	// 1.1 event handling
	public void mouseClicked(MouseEvent e) {
	}
	
	public void mousePressed(MouseEvent e) {
		addMouseMotionListener(this);
		double bestdist = Double.MAX_VALUE;
		int x = e.getX();
		int y = e.getY();
		for (Node n : nodes.values()) {
			double dist = (n.x - x) * (n.x - x) + (n.y - y) * (n.y - y);
			if (dist < bestdist) {
				pick = n;
				bestdist = dist;
			}
		}
		if (pick == null) {
			//repaint();
			e.consume();
			return;
		}
		pickfixed = pick.fixed;
		pick.fixed = true;
		pick.x = x;
		pick.y = y;
		//repaint();
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
		//repaint();
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
			//repaint();
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

	public Node getNode(int id) {
		return nodes.get(new Integer(id));
	}


	
}

@ClassDescription("Spring Layout")
@PluginType(PluginType.SIM_PLUGIN)
public class SpringLayout extends VisPlugin implements ActionListener, ItemListener, Observer {
	private static Logger		logger				= Logger.getLogger(SpringLayout.class);
	
	private final static String failmsg = "This Plugin needs a DGRM.";
	
	
	GraphPanel					panel;
	JPanel						controlPanel;
	Simulation					sim;
	DirectedGraphMedium			radioMedium			= null;
	
	JButton						clear				= new JButton("Clear");
	JButton						shake				= new JButton("Shake");
	ComboBoxItem[]				comboBoxItems		= { 
			new ComboBoxItem("RSSI", GraphPanel.Elength.RSSI),
			new ComboBoxItem("RSSI (Max)", GraphPanel.Elength.RSSI_max), 
			new ComboBoxItem("LQI", GraphPanel.Elength.LQI),
			new ComboBoxItem("LQI (Max)", GraphPanel.Elength.LQI_max) };
	
	JComboBox					layout				= new JComboBox(comboBoxItems);
	
	
	JButton						zoom_in				= new JButton("+");
	JLabel						zoom_lab			= new JLabel("1");
	JButton						zoom_out			= new JButton("-");
	
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
	
	int	edgeHash;
	int	moteHash;
	
	public SpringLayout(Simulation sim,  GUI gui) throws Exception {
		super("SpringLayout", gui);
		
		this.sim = sim;
		
	}
	
	public void startPlugin() {
		
		if (!(sim.getRadioMedium() instanceof DirectedGraphMedium)) {
			JOptionPane.showMessageDialog(this, failmsg, "Unsufficiant environment", JOptionPane.WARNING_MESSAGE);
			add(new JLabel(failmsg));
			
			return;
		}
		
		this.radioMedium = (DirectedGraphMedium) sim.getRadioMedium();
		
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
		controlPanel.add(zoom_lab);
		controlPanel.add(zoom_out);
		zoom_out.addActionListener(this);
		
		
		controlPanel.add(pause);
		pause.addActionListener(this);

		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocation(510, 0);
		this.setSize(520, 320);
		this.setBackground(Color.WHITE);
		updateUI();
		repaint();
		
		// Update Motes first!
		
		updateMotes();
		updateEdges();
		// Register observers
		radioMedium.addRadioMediumObserver(this);
		sim.addObserver(this);
		
		this.start();
		
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
			// panel.nodes.clear();
			// panel.edges.clear();
			return;
		}
		if (src == shake) {
			
			panel.shake();
		}
		if (src == pause) {
			if (!pause.getModel().isSelected()) {
				panel.start();
				//repaint();
			} else {
				panel.stop();
				//repaint();
			}
		}
		if (src == layout) {
			GraphPanel.view = ((ComboBoxItem) layout.getSelectedItem()).getKey();
		}
		
		if (src == zoom_in) {
			GraphPanel.scale += 1;
			zoom_lab.setText((new Float((float)GraphPanel.scale/10)).toString() );
			//repaint();
		}
		if (src == zoom_out) {
			if (GraphPanel.scale > 1) {
				GraphPanel.scale -= 1;
				zoom_lab.setText((new Float((float)GraphPanel.scale/10)).toString() );
			}
			//repaint();
		}
	}
	
	public GraphPanel getPanel() {
		return this.panel;
	}
	
	public void itemStateChanged(ItemEvent e) {
		
	}
	
	void updateEdges() {
		logger.info("updating edges");
		panel.resetEdges();
		for (DirectedGraphMedium.Edge e : radioMedium.getEdges()) {
			int src = e.source.getMote().getID();
			int dst = e.superDest.radio.getMote().getID();
			double rssi = ((DGRMDestinationRadio) e.superDest).signal;
			double lqi = ((DGRMDestinationRadio) e.superDest).lqi;
			logger.info("Edge " + src + " " + dst);
			panel.setEdge(src, dst, rssi, lqi);
			
		}
		panel.removeUnsetEdges();
	}
	
	void updateMotes() {
		logger.info("updating motes");
		ArrayList<Integer> nds = new ArrayList<Integer>(panel.getNodeIds());
		
		for (Mote m : sim.getMotes()) {
			Integer id = m.getID();
			logger.debug("Checking Node" + id);
			
			
			if (!nds.contains(id)) { // Mote does not exist -> Add
				logger.debug("Adding Node" +m.getID());
				panel.addNode(m.getID());
			} else { // Mote exists -> Do net remove it later
				logger.debug("Not removing Node" +m.getID());
				nds.remove(id);
			}
			
		}
		
		for (int n : nds) {
			panel.removeNode(n);
		}
	}
	
	@Override
	public void update(Observable o, Object arg) {
		
		if (o == radioMedium.getRadioMediumObservable()) {
			updateEdges();
		}
		if (o == sim) {
			updateMotes();
			
		}
		
	}
}
