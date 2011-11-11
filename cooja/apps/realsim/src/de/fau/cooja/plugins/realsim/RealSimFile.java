package de.fau.cooja.plugins.realsim;

import java.awt.Color;

import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Element;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.MoteType;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;

@ClassDescription("RealSim File")
@PluginType(PluginType.SIM_PLUGIN)
public class RealSimFile extends VisPlugin implements ActionListener, Observer {
	
	private static Logger	logger			= Logger.getLogger(RealSimFile.class);
	protected Simulation	sim;
	RealSim					rs;
	public JPanel			controlPanel	= new JPanel();
	JTextField				filename		= new JTextField("/home/inf4/morty/tmp/rsdump");
	JButton			select_file		= new JButton("Open File");
	JComboBox				default_node;
	JButton			load			= new JButton("Import");
	
	ArrayList<SimEvent>		events;
	int						pos;
	
	public RealSimFile(Simulation simulation, GUI gui) {
		super("RealSim File", gui, false);
		sim = simulation;
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
	
	int strToId(String str) {
		String[] tok = str.split("\\.");
		int id = new Integer(tok[0]) + new Integer(tok[1]) * 256;
		return id;
	}
	
	private void parsefile(String filename) {
		events = new ArrayList<SimEvent>();
		try {
			BufferedReader sc;
			String line;
			int ln = 0;
			
			sc = new BufferedReader(new FileReader(filename));
			while (null != (line = sc.readLine())) {
				ln++;
				String[] t = line.split(";");
				
				try {
					
					int time = new Integer(t[0]);
					t[1] = t[1].toLowerCase();
					
					if (t[1].equals("addnode")) {
						MoteTypeComboboxModel mtbm = (MoteTypeComboboxModel) default_node.getModel();
						SimEvent se = new SimEventAddNode(time, strToId(t[2]), mtbm.getSelectedMote());
						events.add(se);
					}

					else if (t[1].equals("rmnode")) {
						SimEvent se = new SimEventRmNode(time, strToId(t[2]));
						events.add(se);
					}

					else if (t[1].equals("setedge")) {
						int src = strToId(t[2]);
						int dst = strToId(t[3]);
						double ratio = (new Double(t[4])) / 100;
						double rssi = new Double(t[5]);
						int lqi = new Integer(t[6]);
						SimEvent se = new SimEventSetEdge(time, src, dst, ratio, rssi, lqi);
						events.add((SimEvent) se);
					}

					else if (t[1].equals("rmedge")) {
						int src = strToId(t[2]);
						int dst = strToId(t[3]);
						SimEvent se = new SimEventRmEdge(time, src, dst);
						events.add((SimEvent) se);
					} else {
						logger.warn("Ignoring line " + ln);
					}
					
				} catch (Exception e) {
					// Continue with next line
					logger.warn("Ignoring line " + ln, e);
				}
			}
			
		} catch (Exception e) {
			return;
		}
		sortEvents();
		logger.info("RealSim imported " + events.size() + " events");
	}
	
	protected void sortEvents() {
		Collections.sort(events, new SimEventComperator());
	}
	
	@Override
	public void update(Observable o, Object arg) {
		
		if (arg instanceof Long) {
			if (events == null)
				return;
			SimEvent evt;
			
			Long larg = (Long) arg;
			larg /= 1000; // Turn into ms
			while (pos < events.size() && (evt = events.get(pos)).time <= larg) {
				pos++;
				if (evt.time < larg)
					continue;
				evt.action();
			}
		}
		
	}
	
	public Collection<Element> getConfigXML() {
		Vector<Element> config = new Vector<Element>();
		Element element;
		for (SimEvent se : events) {
			element = new Element("SimEvent");
			element.setText(se.getClass().getName());
			element.setAttribute(new Attribute("time", Long.toString(se.time)));
			
			Collection<Element> interfaceXML = se.getConfigXML();
			if (interfaceXML != null) {
				element.addContent(interfaceXML);
				config.add(element);
			}
			
		}
		return config;
	}
	
	
	public boolean setConfigXML(Collection<Element> configXML, boolean visAvailable) {
		events = new ArrayList<SimEvent>();
		for (Element element : configXML) {
			String name = element.getName();
			
			if (name.equals("SimEvent")) {
				SimEvent se = null;
				String intfClass = element.getText().trim();
				
				Class<? extends SimEvent> rseClass = sim.getGUI().tryLoadClass(this, SimEvent.class, intfClass);
				
				//Class<?> rseClass = null;
				
				
				if (rseClass == null) {
					logger.fatal("Could not load mote interface class: " + intfClass);
					return false;
				}
				
				@SuppressWarnings("rawtypes")
				java.lang.reflect.Constructor constr = null;
				try {
					constr = rseClass.getConstructor(new Class[]{getClass(), int.class,Collection.class} );
					
				} catch (SecurityException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				} catch (NoSuchMethodException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				
				try {
					@SuppressWarnings("rawtypes")
					Object[] para = new Object[] {this, 
							(int)Integer.parseInt(element.getAttribute("time").getValue()), (Collection)element.getChildren() };
					se = (SimEvent) constr.newInstance(para);
				} catch (InstantiationException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IllegalArgumentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (InvocationTargetException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				events.add(se);
				
			}
		}
		sortEvents();
		logger.info("RealSim loaded " + events.size() + " events");
		return true;
	}
	
	abstract static class SimEvent {
		abstract void action();
		
		abstract public  Collection<Element> getConfigXML();
		
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
			if (o1 instanceof SimEventAddNode) { // Make sure nodes come first
				if (o2 instanceof SimEventAddNode) {
					return 0;
				}
				return -1;
			}
			return 0;
		}
		
	}
	
	class SimEventAddNode extends SimEvent {
		int			id	= 0;
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
		
		public Collection<Element> getConfigXML() {
			Vector<Element> config = new Vector<Element>();
			Element el;
			config.add(el = new Element("ID"));
			el.setText(Integer.toString(id));
			config.add(el = new Element("MoteType"));
			el.setText(mt.getIdentifier());
			return config;
		}
		
		public boolean setConfigXML(Collection<Element> configXML) {
			for (Element element : configXML) {
				String name = element.getName().toLowerCase();
				String value = element.getText();
				if (name.equals("id")) {
					id = Integer.parseInt(value);
				}
				if (name.equals("motetype")) {
					mt = sim.getMoteType(value);
				}
				
			}
			if (id == 0 || mt == null)
				return false;
			return true;
		}
		
		public SimEventAddNode(int time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("src or dst not set");
			}
			
		}
		
	}
	
	class SimEventRmNode extends SimEvent {
		int	id;
		
		public SimEventRmNode(int time, int id) {
			super(time);
			this.id = id;
		}
		
		@Override
		public void action() {
			rs.rmMote(id);
		}
		
		@Override
		public Collection<Element> getConfigXML() {
			Vector<Element> config = new Vector<Element>();
			Element el;
			config.add(el = new Element("ID"));
			el.setText(Integer.toString(id));
			return config;
		}
		
		public boolean setConfigXML(Collection<Element> configXML) {
			for (Element element : configXML) {
				String name = element.getName().toLowerCase();
				String value = element.getText();
				if (name.equals("id")) {
					id = Integer.parseInt(value);
				}
				
			}
			if (id == 0)
				return false;
			return true;
		}
		
		public SimEventRmNode(int time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("id not set");
			}
		}
		
	}
	
	class SimEventSetEdge extends SimEvent {
		
		RealSimEdge	rse	= null;
		
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
		
		@Override
		public Collection<Element> getConfigXML() {
			Vector<Element> config = new Vector<Element>();
			Element el;
			el = new Element("RSE");
			el.addContent(rse.getConfigXML());
			config.add(el);
			return config;
		}
		
		public boolean setConfigXML(Collection<Element> configXML) {
			for (Element element : configXML) {
				String name = element.getName().toLowerCase();
				if (name.equals("rse")) {
					@SuppressWarnings("unchecked")
					Collection<Element> children = element.getChildren();
					rse = new RealSimEdge(children);
				}
				
			}
			if (rse == null)
				return false;
			return true;
		}
		
		public SimEventSetEdge(int time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("RSE not set");
			}
		}
		
	}
	
	class SimEventRmEdge extends SimEvent {
		
		RealSimEdge	rse;
		
		public SimEventRmEdge(int time, int src, int dst) {
			super(time);
			rse = new RealSimEdge(src, dst);
			rse.dst = dst;
			
		}
		
		@Override
		void action() {
			rs.rmEdge(rse);
		}
		
		@Override
		public Collection<Element> getConfigXML() {
			Vector<Element> config = new Vector<Element>();
			Element el;
			el = new Element("RSE");
			el.addContent(rse.getConfigXMLShort());
			config.add(el);
			return config;
		}
		
		public boolean setConfigXML(Collection<Element> configXML) {
			for (Element element : configXML) {
				String name = element.getName().toLowerCase();
				if (name.equals("RSE")) {
					@SuppressWarnings("unchecked")
					Collection<Element> children = element.getChildren();
					rse = new RealSimEdge(children);
				}
				
			}
			if (rse == null)
				return false;
			return true;
		}
		
		public SimEventRmEdge(int time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("RSE not set");
			}
		}
	}
	
}
