package se.sics.cooja.plugins;

import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToggleButton;

import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;

class Node {
    double x;
    double y;
    double dx;
    double dy;
    boolean fixed;
    String lbl;
}

class Edge {
    Node from;
    Node to;
    public double len(){
    	return GraphPanel.view == 0 ? rssi : lqi;
    }
    public void setRSSI(double rssi){
    	this.rssi = rssi;
    }
    public void setLQI(double lqi){
    	this.lqi = lqi;
    }
    double rssi;
    double lqi;
    int ttl;
}

class GraphPanel extends Panel implements Runnable, MouseListener, MouseMotionListener {
	private static final long serialVersionUID = 1L;
	public static int view = 0;
	SpringLayout graph;
    ArrayList<Node> nodes = new ArrayList<Node>();
    ArrayList<Edge> edges = new ArrayList<Edge>();
    Thread relaxer;
    Simulation sim;

    public GraphPanel(SpringLayout graph, Simulation sim) {
    	this.graph = graph;
    	this.sim = sim;
    	addMouseListener(this);
    }

    protected Node findNode(String lbl) {
    	for (int i = 0 ; i < nodes.size() ; i++) {
    		if (nodes.get(i).lbl.equals(lbl)) {
    			return nodes.get(i);
    		}
    	}
    	return addNode(lbl);
    }
    
    public Node addNode(String lbl) {
		Node n = new Node();
		n.x = 10 + 380*Math.random();
		n.y = 10 + 380*Math.random();
		n.lbl = lbl;
		if(lbl.equals("80")){
			n.fixed = true;
			n.x = graph.getWidth()/2;
			n.y = graph.getHeight()/2;
		}
		
		nodes.add(n);
		return n;
	}
    
    public void addEdge(String from, String to, int rssi, int lqi) {
		Edge e = new Edge();
		Edge oldedge = null;
		
		for(int i = 0; i < edges.size(); i++){
			if(edges.get(i).from.lbl.equals(from) && edges.get(i).to.lbl.equals(to)){
				oldedge = edges.get(i);
				edges.remove(i);
				i--;
			}
		}
		for(int i = 0; i < edges.size(); i++){
			if(edges.get(i).from.lbl.equals(to) && edges.get(i).to.lbl.equals(from)){
				oldedge = edges.get(i);
				edges.remove(i);
				i--;
			}
		}
		e.from = findNode(from);
		e.to = findNode(to);
		if(oldedge != null) {
			e.setRSSI(Math.round((rssi + oldedge.rssi)/2));
			e.setLQI(Math.round(lqi + oldedge.lqi)/2);
		}
		else {
			e.setRSSI(rssi);
			e.setLQI(lqi);
		}
		e.ttl = 10000;
		edges.add(e);;
    }
    
    synchronized void removeNode(String lbl){
    	for(int i = 0; i < nodes.size(); i++){
    		if(lbl.equals(String.valueOf(nodes.get(i).lbl))){
    			nodes.remove(i);
    			i--;
    		}
    	}
    	for(int i = 0; i < edges.size(); i++){
    		if(lbl.equals(edges.get(i).from.lbl) || lbl.equals(edges.get(i).to.lbl)){
    			edges.remove(i);
    			i--;
    		}
    	}
    }
    
    public void run() {
        Thread me = Thread.currentThread();
		while (relaxer == me) {
		    relax();
		    Visualizer v = (Visualizer)(sim.getGUI().getPlugin("Visualizer"));
			for(Node n: nodes){
				if(n != null && sim.getMoteWithID(new Integer(n.lbl)) != null){
					sim.getMoteWithID(new Integer(n.lbl)).getInterfaces().getPosition().setCoordinates(n.x/50, n.y/50, 0);
					if(v.resetViewport > 0){
						v.resetViewport();
					}
				}
			}
			for(int i = 0; i < edges.size(); i++){
				Edge e = edges.get(i);
				e.ttl--;
				if(e.ttl <= 0){
					edges.remove(e);
					i--;
				}
				if(edges.isEmpty()){
					nodes.clear();
					while(sim.getMotesCount() > 0){
						sim.removeMote(sim.getMote(0));
					}
				}
			}
		    try {
		    	Thread.sleep(100);
		    } catch (InterruptedException e) {
		    	break;
		    }
		}
    }

    synchronized void relax() {
		for (int i = 0 ; i < edges.size() ; i++) {
		    Edge e = edges.get(i);
		    double vx = nodes.get(nodes.indexOf(e.to)).x - nodes.get(nodes.indexOf(e.from)).x;
		    double vy = nodes.get(nodes.indexOf(e.to)).y - nodes.get(nodes.indexOf(e.from)).y;
		    double len = Math.sqrt(vx * vx + vy * vy);
	            len = (len == 0) ? .0001 : len;
		    double f = (edges.get(i).len() - len) / (len * 3);
		    double dx = f * vx;
		    double dy = f * vy;
	
		    nodes.get(nodes.indexOf(e.to)).dx += dx;
		    nodes.get(nodes.indexOf(e.to)).dy += dy;
		    nodes.get(nodes.indexOf(e.from)).dx += -dx;
		    nodes.get(nodes.indexOf(e.from)).dy += -dy;
		}

		for (int i = 0 ; i < nodes.size() ; i++) {
		    Node n1 = nodes.get(i);
		    double dx = 0;
		    double dy = 0;
	
		    for (int j = 0 ; j < nodes.size() ; j++) {
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
			} else if (len < 100*100) {
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
		for (int i = 0 ; i < nodes.size() ; i++) {
		   Node n = nodes.get(i);
		    if (!n.fixed) {
		    	if(Math.abs(Math.max(-10, Math.min(10, n.dx))) > 0.5){
		    		n.x += Math.max(-10, Math.min(10, n.dx));
		    	}
		    	if(Math.abs(Math.max(-10, Math.min(10, n.dy))) > 0.5){
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
		repaint();
    }

    Node pick;
    boolean pickfixed;
    Image offscreen;
    Dimension offscreensize;
    Graphics offgraphics;

    final Color fixedColor = Color.red;
    final Color selectColor = Color.red;
    final Color edgeColor = Color.black;
    final Color nodeColor = Color.white;
    final Color arcColor1 = Color.black;
    final Color arcColor2 = Color.green;
    final Color arcColor3 = Color.red;

    public void paintNode(Graphics g, Node n, FontMetrics fm) {
		int x = (int)n.x;
		int y = (int)n.y;
		g.setColor((n == pick) ? selectColor : (n.fixed ? fixedColor : nodeColor));
		int w = fm.stringWidth(n.lbl) + 10;
		int h = fm.getHeight() + 4;
		g.fillRect(x - w/2, y - h / 2, w, h);
		g.setColor(Color.black);
		g.drawRect(x - w/2, y - h / 2, w-1, h-1);
		g.drawString(n.lbl, x - (w-10)/2, (y - (h-4)/2) + fm.getAscent());
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
	for (int i = 0 ; i < edges.size() ; i++) {
	    Edge e = edges.get(i);
	    int x1 = (int)nodes.get(nodes.indexOf(e.from)).x;
	    int y1 = (int)nodes.get(nodes.indexOf(e.from)).y;
	    int x2 = (int)nodes.get(nodes.indexOf(e.to)).x;
	    int y2 = (int)nodes.get(nodes.indexOf(e.to)).y;
	    int len = (int)Math.abs(Math.sqrt((x1-x2)*(x1-x2) + (y1-y2)*(y1-y2)) - e.len());
	    offgraphics.setColor((len < 10) ? arcColor1 : (len < 20 ? arcColor2 : arcColor3)) ;
	    offgraphics.drawLine(x1, y1, x2, y2);
		offgraphics.drawString(String.valueOf(e.len()),  x1 + (x2-x1)/2, y1 + (y2-y1)/2);
		offgraphics.setColor(edgeColor);
		repaint();
	}

	FontMetrics fm = offgraphics.getFontMetrics();
	for (int i = 0 ; i < nodes.size() ; i++) {
	    paintNode(offgraphics, nodes.get(i), fm);
	}
	g.drawImage(offscreen, 0, 0, null);
    }

    //1.1 event handling
    public void mouseClicked(MouseEvent e) {
    }

    public void mousePressed(MouseEvent e) {
	    addMouseMotionListener(this);
		double bestdist = Double.MAX_VALUE;
		int x = e.getX();
		int y = e.getY();
		for (int i = 0 ; i < nodes.size() ; i++) {
		    Node n = nodes.get(i);
		    double dist = (n.x - x) * (n.x - x) + (n.y - y) * (n.y - y);
		    if (dist < bestdist) {
			pick = n;
			bestdist = dist;
		    }
		}
		if(pick == null){
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
    	if(pick != null){
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
    
    public ArrayList<Node> getNodes(){
    	return this.nodes;
    }

}


@ClassDescription("SpringLayout") 
@PluginType(PluginType.SIM_STANDARD_PLUGIN)
public class SpringLayout extends VisPlugin implements ActionListener, ItemListener {
	private static final long serialVersionUID = 1L;
	GraphPanel panel;
    JPanel controlPanel;
    Simulation sim;

    Button clear = new Button("Clear");
    Button shake = new Button("Shake");
    Button rssi = new Button("RSSI-View");
    Button lqi = new Button("LQI-View");
    
    JToggleButton pause = new JToggleButton("Pause");
	
	public SpringLayout(Simulation sim, GUI gui){
		super("SpringLayout", gui);
		this.sim = sim;
		this.init();
		this.start();
	}

    public void init() {
		panel = new GraphPanel(this,this.sim);
		add("Center", panel);
		controlPanel = new JPanel();
		add("South", controlPanel);
		controlPanel.add(clear); clear.addActionListener(this);
		controlPanel.add(shake); shake.addActionListener(this);
		controlPanel.add(rssi); rssi.addActionListener(this);
		controlPanel.add(lqi); lqi.addActionListener(this);
		controlPanel.add(pause); pause.addActionListener(this);
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocation(510,0);
		this.setSize(520, 320);
		this.setBackground(Color.WHITE);
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
		   panel.nodes.clear();
		   panel.edges.clear();
		   return;
		}
		if (src == shake) {
		    for (int i = 0 ; i < panel.nodes.size() ; i++) {
				Node n = panel.nodes.get(i);
				if (!n.fixed) {
				    n.x += 80*Math.random() - 40;
				    n.y += 80*Math.random() - 40;
				}
		    }
		    repaint();
		}
		if(src == pause){
			if(!pause.getModel().isSelected()){
				panel.start();
				repaint();
			}
			else {
				panel.stop();
				repaint();
			}
		}
		if(src == rssi){
			GraphPanel.view = 0;
		}
		if(src == lqi){
			GraphPanel.view = 1;
		}
    }
    
    public GraphPanel getPanel(){
    	return this.panel;
    }
    
    public void itemStateChanged(ItemEvent e) {
		
	}
}
