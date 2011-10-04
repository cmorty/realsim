package src;

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

@ClassDescription("NodeListener")
@PluginType(PluginType.SIM_STANDARD_PLUGIN)
public class NodeListener extends VisPlugin implements ActionListener, Observer {
	
	private static final long	serialVersionUID	= 4368807123350830772L;
	protected Simulation		sim;
	
	ServerSocket				serverSocket;
	public JPanel				controlPanel		= new JPanel();
	JToggleButton				set_port			= new JToggleButton("Click to start with port:");
	JTextField					insert_port			= new JTextField(4);
	JComboBox					default_node		= new JComboBox();
	
	public NodeListener(Simulation simulation, GUI gui) {
		super("NodeListener", gui);
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
	private SpringLayout		g;
	public Socket				socket;
	private JPanel				controlPanel;
	private JComboBox			default_node;
	
	public Listener(NodeListener nl) throws IOException {
		this.sim = nl.sim;
		this.serverSocket = nl.serverSocket;
		this.radioMedium = (DirectedGraphMedium) sim.getRadioMedium();
		this.radioMedium.clearEdges();
		this.controlPanel = nl.controlPanel;
		this.default_node = nl.default_node;
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
				ArrayList<MyEdge> edges = new ArrayList<MyEdge>();
				Visualizer v = (Visualizer) (sim.getGUI().getPlugin("Visualizer"));
				this.g = (SpringLayout) sim.getGUI().getPlugin("SpringLayout");
				
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
							while (sim.getMotesCount() > 0) {
								sim.removeMote(sim.getMote(0));
							}
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
									sim.removeMote(sim_mote);
									g.getPanel().removeNode(String.valueOf(sim_mote.getID()));
									int rem = 0;
									for (MyEdge e : edges) {
										if (e.getSrc() == sim_mote.getID() || e.getDst() == sim_mote.getID()) {
											rem++;
											break;
										} else {
											rem++;
										}
									}
									if (!edges.isEmpty()) {
										edges.remove(rem - 1);
									}
								}
							}
							
							for (Integer id : motes.keySet()) {
								// Check if Mote already added to Simulation
								MoteType moteT = null;
								
								if (sim.getMoteWithID(id) != null) {
									continue;
								}
								
								// Set Variable for later use
								boolean isRunning = false;
								if (sim.isRunning()) {
									isRunning = true;
								}
								
								MoteType[] mt = sim.getMoteTypes();
								
								// Find MoteType
								// TODO Make this configurable
								for (int cnt = 0; cnt < mt.length; cnt++) {
									moteT = mt[cnt];
									if (moteT.getDescription().equals(default_node.getSelectedItem().toString()))
										break;
									;
								}
								
								if (moteT == null) {
									continue;
								}
								
								if (isRunning) {
									sim.stopSimulation();
								}
								
								Mote mote = moteT.generateMote(sim);
								sim.addMote(mote);
								
								double x = (Math.random() * 10000) % 15;
								double y = (Math.random() * 10000) % 15;
								mote.getInterfaces().getPosition().setCoordinates(x, y, 0);
								mote.getInterfaces().getMoteID().setMoteID(id);
								
								g.getPanel().addNode(String.valueOf(id));
								
								if (isRunning) {
									sim.startSimulation();
								}
								
								// Set Rimeaddress
								byte[] rime_addr = new byte[2];
								rime_addr[0] = id.byteValue();
								rime_addr[1] = motes.get(id).byteValue();
								((AddressMemory) mote.getMemory()).setByteArray("rimeaddr_node_addr", rime_addr);
							}
						}
						
						// Add Edge to DGRMConfigurator
						if (token.equals("edge")) {
							try {
								String s1 = t.nextToken();
								String s2 = t.nextToken();
								int id_src;
								int id_dst;
								MyEdge edge;
								boolean e_exists = false;
								id_src = new Integer(s1.substring(0, s1.indexOf('.')));
								id_dst = new Integer(s2.substring(0, s2.indexOf('.')));
								edge = new MyEdge(id_src, id_dst);
								
								if (!getMotesID().contains(id_src) || !getMotesID().contains(id_dst)) {
									continue lines;
								}
								
								for (MyEdge e : edges) {
									if (e.equals(edge)) {
										e_exists = true;
									}
								}
								
								// Remove old existing edge
								if (e_exists) {
									for (DirectedGraphMedium.Edge e : radioMedium.getEdges()) {
										Radio src = e.source;
										Radio dst = e.superDest.radio;
										if (src.getMote().getID() == edge.getSrc() && dst.getMote().getID() == edge.getDst()) {
											radioMedium.removeEdge(e);
										}
									}
								}
								
								// Add new Edge
								double ratio = new Double(t.nextToken()) / 100.0;
								int rssi = new Integer(t.nextToken());
								int lqi = new Integer(t.nextToken());
								if (ratio <= 0.0 || ratio > 1.0 || rssi > 90 || rssi <= 0 || lqi > 110 || lqi <= 0) {
									g.getPanel().removeEdge(edge);
									continue lines;
								}
								DGRMDestinationRadio dr = new DGRMDestinationRadio(sim.getMoteWithID(id_dst).getInterfaces().getRadio());
								dr.ratio = ratio;
								dr.signal = rssi - 100;
								dr.lqi = lqi;
								DirectedGraphMedium.Edge newEdge = new Edge(sim.getMoteWithID(id_src).getInterfaces().getRadio(), dr);
								radioMedium.addEdge(newEdge);
								edges.add(edge);
								radioMedium.requestEdgeAnalysis();
								g.getPanel().addEdge(String.valueOf(id_src), 
										String.valueOf(id_dst), 
										(int) Math.pow(90 - rssi, 2) / 25,
										(int) ((Math.pow(110 - lqi, 2)) + (100 - (100 * ratio))));
								v.resetViewport++;
								
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
