package de.fau.realsim.cmdhandlers;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import de.fau.realsim.AnalyzedData;
import de.fau.realsim.Connection;
import de.fau.realsim.DataPacket;
import de.fau.realsim.DataPacketHandler;
import de.fau.realsim.RealSimClient;
import de.fau.realsim.output.PacketOutput;
import de.fau.realsim.util.ArgumentManager;

public class Server extends CmdHandler{
	private static Logger logger = Logger.getLogger(Server.class);
	
	public Server(ArgumentManager am) {
		super(am, "server");
		// TODO Auto-generated constructor stub
	}
	
	
	String dumpfile = "dump.rs";
	
	@Override
	public String[] help() {
		ArrayList<String> rv = new ArrayList<String>();	
		rv.add("<Output>");
		rv.add("Anylze File");
		rv.add("<Output> Default: " + dumpfile);
		rv.add("-port\tTCP/IP Port. Default: " + RealSimClient.defaultport);
		rv.add(helpPO);
		return rv.toArray(new String[0]);
	}

	@Override
	public int main() {
		DataPacketHandler dph = this;
		RealSimClient rsc = new RealSimClient(dph);
		
		rsc.port = config.getPropertyAsInt("port", RealSimClient.defaultport);
		if(args.length > 1){
			dumpfile = args[1];
		}
		
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
				logger.info("Saving file");
				
				try {
					FileOutputStream fsout = new FileOutputStream(dumpfile);
					PacketOutput po = getPacketOutput();
					AnalyzedData anl = new AnalyzedData(pkts.toArray(new DataPacket[0]));
					anl.analyze();
					po.output(anl, fsout);
					fsout.close();
				} catch (IOException e) {
					System.err.println(e);
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
		
		try {
			rsct.join(1000);
			if (rsct.isAlive()) {
				logger.warn("Failed to terminate");
				rsc.shutdown(2);
				rsct.join(1000);
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if (rsct.isAlive()) {
			logger.error("Did not terminate");
			System.exit(1);
		}
		return 1;
	}



}
