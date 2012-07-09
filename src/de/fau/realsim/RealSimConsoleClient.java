package de.fau.realsim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import org.apache.log4j.*;

import de.fau.realsim.input.NetstatParser;
import de.fau.realsim.input.PacketInput;
import de.fau.realsim.input.SerialdumpNetstatParser;
import de.fau.realsim.input.SerializedInput;
import de.fau.realsim.output.PacketOutput;
import de.fau.realsim.output.RealSimFile;
import de.fau.realsim.output.SerializedOutput;
import de.fau.realsim.util.ArgumentManager;

public class RealSimConsoleClient implements DataPacketHandler {
	private static Logger logger = Logger.getLogger(RealSimConsoleClient.class);
	ArrayList<DataPacket> pkts = new ArrayList<DataPacket>(500);
	
	static ArgumentManager config = null;
	static String args[];
	static String dumpfile = "dump.rs";
	static String outfile = "network.dat";
	
	/**
	 * @param args
	 * @throws InterruptedException
	 */
	public static void main(String[] args_) throws InterruptedException {
		if (new File("log4j.prop").exists()){
			PropertyConfigurator.configure("log4j.prop");
		} else {
			BasicConfigurator.configure();
		}
		 
		config = new ArgumentManager();
		config.handleArguments(args_);
		
		
		
		args = config.getArguments();
		
		if(args.length == 0){
			printHelp(); 
			System.exit(0);
		}
		RealSimConsoleClient rscc = new RealSimConsoleClient();
		if (args[0].equals("listen")) {
			
			rscc.main_listen();
		} else if (args[0].equals("analyze")) {
			rscc.main_analyze();
			
		} else if (args[0].equals("convert")) {
			rscc.main_convert();
		}else {
			printHelp();
		}
		
	}
	
	static void printHelp(){
		System.out.println(" [listen | analyze | parse <file>]\n"+
				"\n" +
				"listen:\n" +
				"\t-port=<port>\n" +
				"\t-dumpfile=<dumpfile>Defualt: "+ dumpfile + "\n" +
				"\n" +
				"analyze\n" +
				"\t-dumpfile=<dumpfile> Defualt: "+ dumpfile + "\n" );
	}
	
	
	void main_parse() {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(args[1]));
		} catch (FileNotFoundException e) {
			System.err.println("Could not open file: " + args[1]);
			System.exit(1);
		}
		String line = null;
		
		NetstatParser rsp = new NetstatParser(this);
		
		try {
			while ((line = reader.readLine()) != null) {
				rsp.parse(line);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	PacketInput getPacketInput(){
		String inp = config.getProperty("intype", "serial");
		if(inp.equals("serial")){
			return new SerializedInput(this);
		} else if (inp.equals("net")){
			return new NetstatParser(this);
		} else if (inp.equals("dumpnet")){
			return new SerialdumpNetstatParser(this);
		}
		System.err.print("Unknown input type: " + inp + "\n");
		System.exit(1);
		return null;
		
	}
	
	PacketOutput getPacketOutput(){
		String inp = config.getProperty("outtype", "serial");
		if(inp.equals("serial")){
			return new SerializedOutput();
		} else if (inp.equals("real")){
			return new  RealSimFile();
		}
		System.err.print("Unknown output type: " + inp + "\n");
		System.exit(1);
		return null;
		
	}
	
	
	
	void main_convert(){

		int start = config.getPropertyAsInt("start", 0);
		int end = config.getPropertyAsInt("end",Integer.MAX_VALUE);
		boolean crop =config.getPropertyAsBoolean("crop", false);
			
		
		if(args.length < 3){
			System.err.print("Inputfile and Outputfile are missing");
			System.exit(1);
		}
		
		PacketInput pi = getPacketInput();
		PacketOutput po = getPacketOutput();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(args[1]);
			pi.parse(fis);
			fis.close();
		} catch (IOException e) {
			System.err.println(e);
			System.exit(1);
		}
		
		AnalyzedData anl = new AnalyzedData(pkts.toArray(new DataPacket[0]));
		anl.analyze();
		System.out.append(anl.getStats());
		
		
		
		if(crop){
			if(anl.packet_first_last > start) start = anl.packet_first_last;
			if(anl.packet_last_first < end) end = anl.packet_last_first;
			
			if(start >= end){
				System.err.println("Can't crop: Start: "+start+" End: "+end);
				System.exit(1);
			}
			
		}
		

		
		//Dump data
		
		try {
			FileOutputStream fsout = new FileOutputStream(args[2]);
			po.output(anl, fsout, start, end);
			fsout.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


		
		

	}
	
	void main_analyze() {
		InputStream fis = null;
		
		try {
			fis = new FileInputStream(config.getProperty("dumpfile", dumpfile));
			ObjectInputStream o = new ObjectInputStream(fis);
			
			@SuppressWarnings("unchecked")
			ArrayList<DataPacket> readObject = (ArrayList<DataPacket>) o.readObject();
			pkts = readObject;
		} catch (IOException e) {
			System.err.println(e);
		} catch (ClassNotFoundException e) {
			System.err.println(e);
		} finally {
			try {
				fis.close();
			} catch (Exception e) {
			}
		}
		//AnalyzedData anl = new AnalyzedData(scc.pkts.toArray(new DataPacket[scc.pkts.size()]));
		//anl.analyze();
		
	}
	
	void main_listen() throws InterruptedException {
		DataPacketHandler dph = this;
		RealSimClient rsc = new RealSimClient(dph);
		
		rsc.port = config.getPropertyAsInt("port", 1337);
		
		
		Thread rsct = new Thread(rsc);
		rsct.start();
		// Handle stuff
		
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		while (rsct.isAlive()) {
			String cmd;
			try {
				cmd = stdin.readLine().trim();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				break;
			}
			if (cmd.startsWith("s")) {
				OutputStream fos = null;
				logger.info("Saving file");
				
				try {
					fos = new FileOutputStream(config.getProperty("dumpfile", dumpfile));
					SerializedOutput.output(pkts.toArray(new DataPacket[0]), fos);
				} catch (IOException e) {
					System.err.println(e);
				} finally {
					try {
						fos.close();
					} catch (Exception e) {
						System.err.println(e);
					}
				}
			} else if (cmd.startsWith("p")) {
				@SuppressWarnings("unchecked")
				ArrayList<DataPacket> lpkts = (ArrayList<DataPacket>) pkts.clone();
				for (DataPacket dp : lpkts) {
					System.out.printf("Time: %d   Source: %02x.%02x   ID: %3d   Source Time: %4d\n", dp.sts, dp.src & 0xFF,
							(dp.src >> 8) & 0xFF, dp.id, dp.ts);
					for (Connection c : dp.getCns()) {
						System.out.printf("    %02x.%02x   RSSI: %3d   LQI: %3d   RCV: %3d   Loss: %2d   Dup: %2d\n", c.node & 0xFF,
								(c.node >> 8) & 0xFF, c.rssi, c.lqi, c.rcv, c.loss, c.dup);
					}
				}
			} else if (cmd.startsWith("e")) {
				// exit
				rsc.shutdown(2);
				break;
			} else {
				System.out.printf("s: save\n");
				System.out.printf("p: print\n");
				System.out.printf("e: exit\n");
				// Save
			}
		}
		
		rsct.join(1000);
		if (rsct.isAlive()) {
			logger.warn("Failed to terminate");
			rsc.shutdown(2);
			rsct.join(1000);
		}
		if (rsct.isAlive()) {
			logger.error("Did not terminate");
			System.exit(1);
		}
		
	}

	public void handleDataPaket(DataPacket dp) {
		pkts.add(dp);
	}
}
