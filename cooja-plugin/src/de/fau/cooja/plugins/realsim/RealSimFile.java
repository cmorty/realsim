package de.fau.cooja.plugins.realsim;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.DefaultCaret;

import log4j2JText.JTextPaneAppender;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Attribute;
import org.jdom.Element;

import org.contikios.cooja.ClassDescription;
import org.contikios.cooja.Cooja;
import org.contikios.cooja.MoteType;
import org.contikios.cooja.PluginType;
import org.contikios.cooja.Simulation;
import org.contikios.cooja.TimeEvent;
import org.contikios.cooja.VisPlugin;
import org.contikios.cooja.radiomediums.DirectedGraphMedium;

@ClassDescription("RealSim File")
@PluginType(PluginType.SIM_PLUGIN)
public class RealSimFile extends VisPlugin implements ActionListener {
	
	private static Logger	logger			= Logger.getLogger(RealSimFile.class);
	protected static Simulation	sim;
	
	RealSim					rs;
	public JPanel			controlPanel	= new JPanel();
	JTextField				filename		= new JTextField("/home/inf4/morty/tmp/rsdump");
	JButton			select_file		= new JButton("Open File");
	JComboBox				default_node;
	JButton			load			= new JButton("Import");
	Cooja cooja;
	JCheckBox loadFile			= new JCheckBox("Load from File instead of Simulation");
	JTextPane logOutput          = new JTextPane();
	private final static String failmsg = "This Plugin needs a DGRM.";
	
	ArrayList<SimEvent>		events = new ArrayList<SimEvent>(); //Make sure there is an empty list.
	int						pos;
	

	
	
	public RealSimFile(Simulation simulation, Cooja cooja) {
		super("RealSim File", cooja, false);
		sim = simulation;
		this.cooja = cooja;
	}
	
	public void startPlugin() {
		
		if (!(sim.getRadioMedium() instanceof DirectedGraphMedium)) {
			logger.error(failmsg);
			if(Cooja.isVisualized()){
				JOptionPane.showMessageDialog(this, failmsg, "Unsufficiant environment", JOptionPane.WARNING_MESSAGE);
				add(new JLabel(failmsg));
			}
			return;
		}
		JTextPaneAppender taa = new JTextPaneAppender(logOutput);
		
		logger.addAppender(taa);
		taa.setThreshold(Level.ALL);
		System.out.println("TH: " + taa.getThreshold().toString());
		
		//Init components
		default_node = new JComboBox(new MoteTypeComboboxModel(sim));
		
		filename.addActionListener(this);
		select_file.addActionListener(this);
		load.addActionListener(this);
		loadFile.addActionListener(this);
		DefaultCaret caret = (DefaultCaret)logOutput.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
		
		
		
		//Layout
		GroupLayout layout = new GroupLayout(controlPanel);
		controlPanel.setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);
		
		
		
		layout.setVerticalGroup(layout.createSequentialGroup()
				.addComponent(filename, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
				          GroupLayout.PREFERRED_SIZE)
		    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
		        
		     )
		    .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
		    	.addComponent(select_file)
		    	.addComponent(load)
		    )
		    .addComponent(default_node,  GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
		            GroupLayout.PREFERRED_SIZE)
		    .addComponent(loadFile,  GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
				            GroupLayout.PREFERRED_SIZE)
			.addComponent(logOutput)
		);
		
		layout.setHorizontalGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
				.addComponent(filename)
				.addGroup(layout.createSequentialGroup()
					.addComponent(select_file)
					.addComponent(load)	
				)
				.addComponent(default_node)
				.addComponent(loadFile)
				.addComponent(logOutput)
		);
		
		
/*
		   GroupLayout.SequentialGroup hGroup = layout.createSequentialGroup();
		   hGroup.addGroup(layout.createParallelGroup().
		            addComponent(filename).addComponent(select_file));
		   hGroup.addGroup(layout.createParallelGroup().
		            addComponent(default_node).addComponent(load));
		   layout.setHorizontalGroup(hGroup);
		   

		   GroupLayout.SequentialGroup vGroup = layout.createSequentialGroup();

		   vGroup.addGroup(layout.createParallelGroup(Alignment.BASELINE).
		            addComponent(filename).addComponent(default_node));
		   vGroup.addGroup(layout.createParallelGroup(Alignment.BASELINE).
		            addComponent(select_file).addComponent(load));
		   layout.setVerticalGroup(vGroup);
	*/	
		
		add("Center",  new JScrollPane(controlPanel));
		
		rs = new RealSim(sim, cooja);
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
		//Motes in Cooja are little endian!
		int id = new Integer(tok[1]) * 256 + new Integer(tok[0]);
		return id;
	}
	
	private boolean parsefile(String filename) {
		events = new ArrayList<SimEvent>();
		try {
			BufferedReader sc = null;
			String line;
			int ln = 0;
			try{
				sc = new BufferedReader(new FileReader(filename));
			} catch(Exception e){
				logger.fatal("Unable to open file: " + filename);
				if (Cooja.isVisualized()) {
					JOptionPane.showMessageDialog(Cooja.getTopParentContainer(),
							"Unable to open File: " + filename, 
							"RealSimFile - Error",
							JOptionPane.ERROR_MESSAGE);
				}
		                
				return false;
			}
			logger.info("Reading " + filename);
			
			MoteTypeComboboxModel mtbm = (MoteTypeComboboxModel) default_node.getModel();
			if(mtbm.getSelectedItem() == null){
				logger.error("No default node selected.");
				sc.close();
				return false;
			}
			
			while ( null != (line = sc.readLine())) {
				ln++;
				
				if(line.length() == 0) continue;
				
				String[] t = line.split(";");
				String exreason = null;
				int exind = 0;
				try {
					
					exind = 1; exreason = "Time";
					long time = new Long(t[0]);
					t[1] = t[1].toLowerCase();
					
					if (t[1].equals("addnode")) {						
						exind = 2; exreason = "addnode";
						SimEvent se = new SimEventAddNode(time, strToId(t[2]), mtbm.getSelectedMote());
						events.add(se);
					}

					else if (t[1].equals("rmnode")) {
						exind = 2; exreason = "node Id";
						SimEvent se = new SimEventRmNode(time, strToId(t[2]));
						events.add(se);
					}

					else if (t[1].equals("setedge")) {
						exind = 2; exreason = "source Id";
						int src = strToId(t[2]);
						exind = 3; exreason = "target Id";
						int dst = strToId(t[3]);
						exind = 4; exreason = "Ratio";
						double ratio = (new Double(t[4].replace(',', '.'))) / 100;
						exind = 5; exreason = "RSSI";
						double rssi = new Double(t[5].replace(',', '.'));
						exind = 6; exreason = "LQI";
						int lqi = new Integer(t[6]);
						SimEvent se = new SimEventSetEdge(time, src, dst, ratio, rssi, lqi);
						events.add((SimEvent) se);
					}

					else if (t[1].equals("rmedge")) {
						exind = 2; exreason = "source Id";
						int src = strToId(t[2]);
						exind = 2; exreason = "target Id";
						int dst = strToId(t[3]);
						SimEvent se = new SimEventRmEdge(time, src, dst);
						events.add((SimEvent) se);
					} else {
						logger.warn("Unknow command in line " + ln + ". - Igrnoring" );
					}
					
				} catch (Exception e) {
					// Continue with next line
					logger.warn("Could not pase " + exreason + " in line "+ ln + ". (\"" + t[exind] + "\"). - Ignoring");
				}
			}
			sc.close();
			
		} catch (Exception e) {
			return false;
		}
		sortEvents();
		logger.info("RealSim imported " + events.size() + " events");
		//When setting up a new simulation everything a 0 is a automtically added.
		if(sim.getSimulationTime() == 0) {
			for(SimEvent e : events) {
				if(e.time != 0) break;
				e.action();
			}
		}
		//Register event
		eventShed.remove();
		sim.scheduleEvent(eventShed,sim.getSimulationTime());
		logger.debug("Registered Event for: " + sim.getSimulationTime() );
		return true;
	}
	
	private void sortEvents() {
		Collections.sort(events, new SimEventComperator());
	}
	
	
	
	
	private TimeEvent eventShed = new TimeEvent(0) {
	    public void execute(long time) {
	    	if (events == null) return;
			
			time /= Simulation.MILLISECOND; // Turn into ms
			while (pos < events.size()) {
				SimEvent evt = events.get(pos);
				if(evt.time > time){
					//Reshedule
					logger.info("Resheduled for " + (evt.time * Simulation.MILLISECOND) );
					sim.scheduleEvent(this,evt.time * Simulation.MILLISECOND);
					break;
				}
				
				pos++;
				if (evt.time < time){
					logger.info("Dropping RealSimEvent:" + evt.toString() + " @  " + evt.time);
					continue;
				}
				evt.action();
			}
			
	    }

	      
	};
	  
	 
	
	public Collection<Element> getConfigXML() {
		Vector<Element> config = new Vector<Element>();
		Element element;
		element = new Element("Filename");
		element.setText(cooja.createPortablePath(new File(filename.getText())).getPath());
		config.add(element);
		
		config.add(new Element("Load").setText(loadFile.isSelected()?"true":"false"));
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
			if(name.equals("Filename")){
				filename.setText(cooja.restorePortablePath(new File(element.getText())).getPath());
			}
			if(name.equals("Load")){
				loadFile.setSelected(element.getText().toLowerCase().equals("true"));
			}
			//Load anyway....
			if (name.equals("SimEvent")) {
				
				
				SimEvent se = null;
				String intfClass = element.getText().trim();
				
				Class<? extends SimEvent> rseClass = sim.getCooja().tryLoadClass(this, SimEvent.class, intfClass);
				
				//Class<?> rseClass = null;
				
				
				if (rseClass == null) {
					logger.fatal("Could not load mote interface class: " + intfClass);
					return false;
				}
				
				@SuppressWarnings("rawtypes")
				java.lang.reflect.Constructor constr = null;
			
				try {
					constr = rseClass.getConstructor(new Class[]{getClass(), long.class, Collection.class} );
					@SuppressWarnings("rawtypes")
					Object[] para = new Object[] {this, 
							(long)Long.parseLong(element.getAttribute("time").getValue()), (Collection)element.getChildren() };
					se = (SimEvent) constr.newInstance(para);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					logger.error("Something went wrong creating a new SimEvent: " + intfClass , e);
				}
				
				events.add(se);
				
			}
		}
		if(loadFile.isSelected()){
			if(!parsefile(filename.getText())){
				logger.info("As teh file " +filename.getText() + " could not be loaded, the" +
						"settings from the simulation are used.");
			}
		}
		
		sortEvents();
		logger.info("RealSim loaded " + events.size() + " events");
		sim.scheduleEvent(eventShed,sim.getSimulationTime());
		logger.info("Registered Event for: " + sim.getSimulationTime() );
		return true;
	}
	
	abstract static class SimEvent {
		abstract void action();
		
		abstract public  Collection<Element> getConfigXML();
		
		public final long		time;
		static RealSim	rs;
		
		static void setRs(RealSim lrs) {
			if (rs == null)
				rs = lrs;
		}
		
		public SimEvent(long time) {
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
		
		public SimEventAddNode(long time, int id, MoteType mt) {
			super(time);
			this.id = id;
			this.mt = mt;
		}
		
		public SimEventAddNode(long time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("src or dst not set");
			}
			
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
		
		
		
	}
	
	class SimEventRmNode extends SimEvent {
		int	id;
		
		public SimEventRmNode(long time, int id) {
			super(time);
			this.id = id;
		}
		
		public SimEventRmNode(long time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("id not set");
			}
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
		

		
	}
	
	class SimEventSetEdge extends SimEvent {
		
		RealSimEdge	rse	= null;
		
		public SimEventSetEdge(long time, int src, int dst, double ratio, double rssi, int lqi) {
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
		
		public SimEventSetEdge(long time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("RSE not set");
			}
		}
		
	}
	
	class SimEventRmEdge extends SimEvent {
		
		RealSimEdge	rse;
		
		public SimEventRmEdge(long time, int src, int dst) {
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
				String name = element.getName().toUpperCase();
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
		
		public SimEventRmEdge(long time, Collection<Element> configXML) {
			super(time);
			if (!setConfigXML(configXML)) {
				throw new IllegalArgumentException("RSE not set");
			}
		}
	}
	
}
