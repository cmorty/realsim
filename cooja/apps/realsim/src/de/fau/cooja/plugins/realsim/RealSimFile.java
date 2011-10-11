package de.fau.cooja.plugins.realsim;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Comparator;
import java.util.Observable;
import java.util.Observer;
import java.util.PriorityQueue;
import java.util.Scanner;
import java.util.StringTokenizer;

import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;

import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.MoteType;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;

@ClassDescription("RealSim File")
@PluginType(PluginType.SIM_PLUGIN)

public class RealSimFile extends VisPlugin implements ActionListener, Observer {
	protected Simulation	sim;
	RealSim					rs;
	public JPanel			controlPanel	= new JPanel();
	JTextField				filename		= new JTextField("/home/inf4/morty/tmp/rsdump");
	JToggleButton			select_file		= new JToggleButton("Open File");
	JComboBox				default_node ;
	JToggleButton			load			= new JToggleButton("Load");
	PriorityQueue<SimEvent>	events;
	
	public RealSimFile(Simulation simulation, GUI gui) {
		super("RealSim File", gui, false);
		events = new PriorityQueue<SimEvent>(100, new SimEventComperator());
		this.sim = simulation;
	}
	
	public void startPlugin() {
		// Do not start if we do not support the medium
		sim.addMillisecondObserver(this);
		sim.addObserver(this);
		
		default_node = new JComboBox(new MoteTypeComboboxModel(sim));
		
		add("Center", controlPanel);
		controlPanel.add(filename);
		filename.addActionListener(this);
		controlPanel.add(select_file);
		select_file.addActionListener(this);
		
		controlPanel.add(load);
		load.addActionListener(this);
		
		controlPanel.add(default_node);
		
		
		rs = new RealSim(sim);
		SimEvent.setRs(rs);
		
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setSize(180, 190);
		this.setLocation(320, 0);
		this.setBackground(Color.WHITE);
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();
		if (src == select_file) {
			JFileChooser fc = new JFileChooser();
			int fcrv = fc.showOpenDialog(controlPanel);
			if (fcrv == JFileChooser.APPROVE_OPTION) {
				filename.setText(fc.getSelectedFile().getPath());
			}
		}
		if (src == load) {
			parsefile(filename.getText());
		}
		
	}
	
	int strToId(String str){
		String [] tok = str.split("\\.");
		int id = new Integer(tok[0]) + new Integer(tok[1])*256;
		return id;
	}
	
	private void parsefile(String filename) {
		
		try {
			BufferedReader sc;
			String line;
			int ln = 0;
			
			sc = new BufferedReader(new FileReader(filename));
			while (null != (line = sc.readLine())) {
				ln ++;
				String[] t = line.split(";");
				
				try {
					
					int time = new Integer(t[0]);
					t[1] = t[1].toLowerCase();
					
					if (t[1].equals("addnode")) {
						MoteTypeComboboxModel mtbm = (MoteTypeComboboxModel) default_node.getModel();
						SimEvent se = new SimEventAddNode(time, strToId(t[2]), mtbm.getSelectedMote());
						events.add(se);
						
					}
					
					else if (t[1].equals("setedge")) {
						int src = strToId(t[2]);
						int dst = strToId(t[3]);
						double ratio = (new Double(t[4]))/100;
						double rssi = new Double(t[5]);
						int lqi = new Integer(t[6]);
						SimEvent se = new SimEventSetEdge(time, src, dst, ratio, rssi, lqi);
						events.add((SimEvent)se);
					}
					else {
						System.out.println("Ignoring line "+ ln);
					}
					
				} catch (Exception e) {
					// Continue with next line
					System.out.println("Ignoring line "+ ln);
				}
			}
			
		} catch (Exception e) {
			return;
		}
		
	}
	
	@Override
	public void update(Observable o, Object arg) {
		
		if(arg instanceof Long){
			Long larg = (Long) arg;
			larg /= 1000; //Turn into ms
			while(events.size() > 0 && larg >= events.peek().time){
				events.poll().action();
			}
		}
		
	}
	
	abstract static class SimEvent {
		abstract void action();
		
		public long		time;
		static RealSim	rs;
		
		static void setRs(RealSim lrs) {
			if (rs == null)
				rs = lrs;
		}
		
		public SimEvent(int time) {
			this.time = time;
			// TODO Auto-generated constructor stub
		}
	}
	
	public class SimEventComperator implements Comparator<SimEvent> {
		
		@Override
		public int compare(SimEvent o1, SimEvent o2) {
			if (o1.time < o2.time)
				return -1;
			if (o1.time > o2.time)
				return +1;
			if(o1 instanceof SimEventAddNode){ //Make sure nodes come first
				if(o2 instanceof SimEventAddNode){
					return 0;
				}
				return -1;
			}
			return 0;
		}
		
	}
	
	class SimEventAddNode extends SimEvent {
		int			id;
		MoteType	mt	= null;
		
		public SimEventAddNode(int time, int id, MoteType mt) {
			super(time);
			this.id = id;
			this.mt = mt;
		}
		
		@Override
		void action() {
			rs.addmote(id, mt);
		}
		
	}
	
	class SimEventSetEdge extends SimEvent {
		
		RealSimEdge	rse;
		
		public SimEventSetEdge(int time, int src, int dst, double ratio, double rssi, int lqi) {
			super(time);
			rse = new RealSimEdge(src, dst);
			rse.dst = dst;
			rse.ratio = ratio;
			rse.rssi = rssi;
			rse.lqi = lqi;
		}
		
		@Override
		void action() {
			rs.setEdge(rse);
		}
		
	}
	
}
