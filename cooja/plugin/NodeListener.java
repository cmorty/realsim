package se.sics.cooja.plugins;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import se.sics.cooja.AddressMemory;
import se.sics.cooja.ClassDescription;
import se.sics.cooja.GUI;
import se.sics.cooja.Mote;
import se.sics.cooja.MoteInterface;
import se.sics.cooja.PluginType;
import se.sics.cooja.Simulation;
import se.sics.cooja.VisPlugin;
import se.sics.cooja.mspmote.SkyMoteType;
import se.sics.cooja.mspmote.interfaces.SkyByteRadio;
import se.sics.cooja.radiomediums.DGRMDestinationRadio;
import se.sics.cooja.radiomediums.DirectedGraphMedium;
import se.sics.cooja.radiomediums.DirectedGraphMedium.Edge;

@ClassDescription("NodeListener") 
@PluginType(PluginType.SIM_STANDARD_PLUGIN)
public class NodeListener extends VisPlugin {
	
	private static final long serialVersionUID = 4368807123350830772L;
	protected static final int PORT = 1337;
	private Simulation sim;
	private ServerSocket serverSocket;

	public NodeListener(Simulation simulation, GUI gui) throws IOException {
		super("NodeListener", gui);
		this.sim = simulation;
		
		this.setSize(180, 190);
		this.setLocation(320, 0);
		this.setBackground(Color.WHITE);
		this.setDefaultCloseOperation(VisPlugin.EXIT_ON_CLOSE);
		serverSocket = new ServerSocket(PORT);
		
		// Start listening thread
		Listener l = new Listener(sim, serverSocket);
		l.start();
	}
	
	protected void finalize(){
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

class Listener extends Thread {
	
	private Simulation sim;
	private DirectedGraphMedium radioMedium = null;
	private ServerSocket serverSocket;
	private SpringLayout g;
	
	public Listener(Simulation simulation, ServerSocket serverSocket){
		this.sim = simulation;
		this.serverSocket = serverSocket;
		this.radioMedium = (DirectedGraphMedium)sim.getRadioMedium();
		this.radioMedium.clearEdges();	
	}
	
	public void run() {
		try {
			while(true){
				Socket socket = serverSocket.accept();
				InputStream in = socket.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(in));
				String line;
				HashMap<Integer,Integer> motes = new HashMap<Integer,Integer>();
				ArrayList<MyEdge> edges = new ArrayList<MyEdge>();
				Visualizer v = (Visualizer)(sim.getGUI().getPlugin("Visualizer"));
				this.g = (SpringLayout)sim.getGUI().getPlugin("SpringLayout");
				
		lines:	while((line = reader.readLine()) != null){
					if(!line.contains("::") || line.contains("MAC"))continue;
					StringTokenizer t = new StringTokenizer(line, "::");
					while(t.hasMoreElements()){
						String token = t.nextToken();
						
						// Clear all Nodes
						if(token.equals("clear")){
							  while (sim.getMotesCount() > 0) {
								  sim.removeMote(sim.getMote(0));
							  }
						}
						
						// Fill internal mote Array
						if(token.equals("node")){
							while(t.hasMoreElements()){
								String s = t.nextToken();
								if(s.length() > 4 && !s.equals("node")){
									try {
										Integer id1 = new Integer(s.substring(0, s.indexOf('.')));
										Integer id2 = new Integer(s.substring(s.indexOf('.')+1,s.length()));
										motes.put(id1, id2);
									} catch (NumberFormatException e){
										continue lines;
									} catch (StringIndexOutOfBoundsException e) {
										continue lines;
									}
								}
							}
							
							// Check for mote removal (remove also from edges)
							for(Mote sim_mote : sim.getMotes()){
								if(!motes.containsKey(sim_mote.getID())){
									sim.removeMote(sim_mote);
									g.getPanel().removeNode("" + sim_mote.getID());
									int rem = 0;
									for(MyEdge e: edges){
										if(e.getSrc() == sim_mote.getID() || e.getDst() == sim_mote.getID()){
											rem++;
											break;
										}
										else {
											rem++;
										}	
									}
									if(!edges.isEmpty()){
										edges.remove(rem-1);
									}
								}
							}
							
							for(Integer id: motes.keySet()){
								// Check if Mote already added to Simulation
								if(sim.getMoteWithID(id) != null){
									continue;
								}
								
								// Set Variable for later use
								boolean isRunning = false;
								if(sim.isRunning()){
									isRunning = true;
								}
								
								SkyMoteType sky = new SkyMoteType();
								sky.setDescription("Sky Mote #1");
								sky.setIdentifier("sky1");
								ArrayList<Class<? extends MoteInterface>> interfaces = new ArrayList<Class<? extends MoteInterface>>();
								interfaces.add(se.sics.cooja.interfaces.Position.class);
								interfaces.add(se.sics.cooja.interfaces.RimeAddress.class);
								interfaces.add(se.sics.cooja.interfaces.IPAddress.class);
								interfaces.add(se.sics.cooja.interfaces.Mote2MoteRelations.class);
								interfaces.add(se.sics.cooja.interfaces.MoteAttributes.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.MspClock.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.MspMoteID.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.SkyButton.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.SkyFlash.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.SkyCoffeeFilesystem.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.SkyByteRadio.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.MspSerial.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.SkyLED.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.MspDebugOutput.class);
								interfaces.add(se.sics.cooja.mspmote.interfaces.SkyTemperature.class);
								Class<? extends MoteInterface>[] classes = (Class[])interfaces.toArray(new Class[interfaces.size()]);
								sky.setMoteInterfaceClasses(classes);
								
								if(isRunning){
									sim.stopSimulation();
								}
								
								// Set Firmware and Simulation (Sink is id 80)
								sky.setContikiFirmwareFile(new File("/media/E/Doc/Uni/Cooja/examples/sky/scan-neighbors.sky"));
								sky.setSimulation(sim);
								
								// Generate Mote, set Position(random) and add to Simulation
								Mote mote = sky.generateMote(sim);
								double x = (Math.random()*10000)% 15;
								double y = (Math.random()*10000)% 15;
								mote.getInterfaces().getPosition().setCoordinates(x, y, 0);
								mote.getInterfaces().getMoteID().setMoteID(id);
								if(sim.getMoteTypes().length == 0)sim.addMoteType(sky);
								sim.addMote(mote);
								g.getPanel().addNode(id+"");
								
								if(isRunning){
									sim.startSimulation();
								}
								
								// Set Rimeaddress
								byte[] rime_addr = new byte[2];
								rime_addr[0] = id.byteValue();
								rime_addr[1] = motes.get(id).byteValue();
								((AddressMemory)mote.getMemory()).setByteArray("rimeaddr_node_addr", rime_addr);
							}
						}
						
						// Add Edge to DGRMConfigurator
						if(token.equals("edge")){
							String s1 = t.nextToken();
							String s2 = t.nextToken();
							int id_src;
							int id_dst;
							MyEdge edge;
							boolean e_exists = false;
							try {
								id_src = new Integer(s1.substring(0, s1.indexOf('.')));
								id_dst = new Integer(s2.substring(0, s2.indexOf('.')));
								edge = new MyEdge(id_src, id_dst);
								
							} catch (NumberFormatException e){
								continue lines;
							} catch (StringIndexOutOfBoundsException e){
								continue lines;
							}
							
							if(!sim.getMotesID().contains(id_src) || !sim.getMotesID().contains(id_dst)){
								continue lines;
							}

							for(MyEdge e: edges){
								if(e.equals(edge)){
									e_exists = true;
								}
							}
							
							// Remove old existing edge
							if(e_exists){
								for(DirectedGraphMedium.Edge e : radioMedium.getEdges()){
									SkyByteRadio src = (SkyByteRadio) e.source;
									SkyByteRadio dst = (SkyByteRadio) e.superDest.radio;
									if(src.getMote().getID() == edge.getSrc() && dst.getMote().getID() == edge.getDst()){
										radioMedium.removeEdge(e);
									}
								}
							}
							
							// Add new Edge
							try {
								double ratio = new Double(t.nextToken()) / 100.0;
								if(ratio <= 0.2)continue lines;
								int rssi = new Integer(t.nextToken());
								int lqi = new Integer(t.nextToken());
								DGRMDestinationRadio dr = new DGRMDestinationRadio(sim.getMoteWithID(id_dst).getInterfaces().getRadio());
								dr.ratio = ratio;
								dr.signal = rssi - 100;
								DirectedGraphMedium.Edge newEdge = new Edge( sim.getMoteWithID(id_src).getInterfaces().getRadio(),dr);
								radioMedium.addEdge(newEdge);
								edges.add(edge);
								radioMedium.requestEdgeAnalysis();
								g.getPanel().addEdge(id_src+"",id_dst+"", ((100 * 90) - (int)((lqi * rssi) * ratio)) / 50);
								v.resetViewport++;
								
								// Ignore those Exceptions
							} catch(NumberFormatException e){
								
							} catch(NoSuchElementException e){
								
							}
						}
					}
					motes.clear();
				}
			}
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}
	
	// Help class
	private class MyEdge {
		
		private int src;
		private int dst;
		
		public MyEdge(int src, int dst){
			this.src = src;
			this.dst = dst;
		}
		
		public int getSrc(){
			return this.src;
		}
		
		public int getDst(){
			return this.dst;
		}
		
		public boolean equals(MyEdge e){
			return (this.getSrc() == e.getSrc() &&  this.getDst() == e.getDst()) ? true : false;
		}
	}
}