package de.fau.realsim;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.log4j.Logger;

import de.fau.realsim.input.NetstatParser;

public class RealSimClient implements Runnable{
	private static Logger logger = Logger.getLogger(RealSimClient.class);
	
	public final static Integer defaultport = new Integer(1337);
	public Integer port = defaultport;
	ServerSocket ls;
	Socket s;
	
	
	
	DataPacketHandler dph;
	private int sd = 0; //1 Finish data packet; 2 Finish now!
	
	public RealSimClient(DataPacketHandler dph){
		this.dph = dph;
	}
	
	public void shutdown(int sdm){
		if(sdm == 1) sd = 1;
		if(sdm == 2 ) {
			sd = 2;
			try {
				if(s != null && !s.isClosed()){
					s.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
			
				if(ls != null && !ls.isClosed()) ls.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void run() {
		NetstatParser rsp = new NetstatParser(dph);
		// TODO Auto-generated method stub
		logger.info("Starting logger on port " + port.toString());
		try {
			 ls= new ServerSocket(port);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.error("Could not open port " + port);
			return;
		}
		logger.info("Waiting for connection on " + new Integer(ls.getLocalPort()).toString());
		while (true) {
			if( sd != 0 ) break; 
			
			BufferedReader reader;
			try {
				s = ls.accept();
				reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				if(sd == 0){
					logger.error("Something went wrong while accepting connections.\n" + e.getMessage());
				}
				break;
			}
			logger.debug("Connected: " + s.getInetAddress().getHostName());
			String line;
			try {
				
				while ((line = reader.readLine()) != null) {
					if(sd == 2 || sd == 1 && (!rsp.receivingDatapacket())){
						s.close();
						break;
					}
					rsp.parse(line);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			logger.info("Connection closed");
			
		}
		//Close socket
		try {
			ls.close();
		} catch (IOException e) {
			//No need to catch this.
		}
		
	}
	
	
	
	
}

