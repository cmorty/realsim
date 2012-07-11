package de.fau.realsim.cmdhandlers;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import org.apache.log4j.Logger;

import de.fau.realsim.DataPacket;
import de.fau.realsim.DataPacketHandler;
import de.fau.realsim.input.NetstatParser;
import de.fau.realsim.input.PacketInput;
import de.fau.realsim.input.SerialdumpNetstatParser;
import de.fau.realsim.input.SerializedInput;
import de.fau.realsim.output.PacketOutput;
import de.fau.realsim.output.RealSimFile;
import de.fau.realsim.output.SerializedOutput;
import de.fau.realsim.util.ArgumentManager;

abstract public class CmdHandler implements DataPacketHandler  {
	private static Logger logger = Logger.getLogger(Server.class);
	
	String args[];
	
	ArgumentManager config = null;
	
	ArrayList<DataPacket> pkts = new ArrayList<DataPacket>(500);
	
	public final String name;
	final String helpPI = "-intype=<Intype>";
	final String helpPO = "-outtpye=<Outttype>";
	
	ArrayList<PacketInput> in = null;
	ArrayList<PacketOutput> out = null;
	
	@SuppressWarnings("rawtypes")
	static final Class[] inputs = {(Class)SerializedInput.class, NetstatParser.class, SerialdumpNetstatParser.class };
	@SuppressWarnings("rawtypes")
	static final Class[] outputs = {(Class) SerializedOutput.class, (Class) RealSimFile.class};
	
	 
	CmdHandler(ArgumentManager am, String name){
		config = am;
		args = config.getArguments();
		this.name = name;
			
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static String getString(Class cl, String func){
		
		try {
			Method f = cl.getMethod(func, new Class[]{});
			if(f == null) {
				logger.error("Funktion " + func + " not found.");
				return "";
			}
			String rv = (String)f.invoke(new Object[]{});
			
			return  rv;
		} catch (Exception e) {
			
			
			StringWriter sw = new StringWriter();
			sw.write("Class: " + cl.getName() + " Func: " + func + "\n");
			
			e.printStackTrace(new PrintWriter(sw));
			logger.error(sw.toString());
			return null;
		}
	}
	
	
	
	public static String[] getIntypes(){
		ArrayList<String> rv = new ArrayList<String>();
		
		
		for(@SuppressWarnings("rawtypes") Class pi : inputs){

			String name = getString(pi, "getName");
			String help = getString(pi, "getHelp");

			
			if(name == null || help == null) continue;
			
			rv.add(name + "\t" + help);
		}
		return rv.toArray(new String[0]);
	}
	
	
	public static String[] getOuttypes(){
		ArrayList<String> rv = new ArrayList<String>();
		for(@SuppressWarnings("rawtypes") Class po : outputs){
			rv.add(getString(po, "getName") + "\t" + getString(po, "getHelp"));
		}
		return rv.toArray(new String[0]);
	}
	
	
	
	public void handleDataPaket(DataPacket dp) {
		System.out.printf("PKTSS: %d\n" , pkts.size());
		pkts.add(dp);
		
	}
	
	@SuppressWarnings("unchecked")
	PacketInput getPacketInput(){
		String inp = config.getProperty("intype", "serial").toLowerCase();
		for(@SuppressWarnings("rawtypes")Class pi : inputs){
			if(getString(pi, "getName").toLowerCase().equals(inp)){
				try {
					return (PacketInput)  pi.getConstructor(new Class[]{ DataPacketHandler.class} ).newInstance(new Object[]{ this} );
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.error(sw.toString());
					return null;
				}	
			}
			
		}
		System.err.print("Unknown input type: " + inp + "\n");
		System.exit(1);
		return null;
		
	}
	
	@SuppressWarnings("unchecked")
	PacketOutput getPacketOutput(){
		String inp = config.getProperty("outtype", "serial");
		for(@SuppressWarnings("rawtypes") Class po : outputs){
			if(getString(po, "getName").toLowerCase().equals(inp)){
				
				try {
					return (PacketOutput)  po.getConstructor(new Class[]{}).newInstance(new Object[]{} );
				} catch (Exception e) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					logger.error(sw.toString());
					return null;
				}
			}
			
		}
		System.err.print("Unknown output type: " + inp + "\n");
		System.exit(1);
		return null;
		
	}
	
	
	public abstract String[] help();
	
	public abstract int main(); 
}
