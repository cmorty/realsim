package de.fau.realsim.input;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import de.fau.realsim.DataPacketHandler;

public abstract class PacketInput {
	DataPacketHandler dph;
	public PacketInput(DataPacketHandler dph){
		this.dph = dph;
	}
	
	public  static String getName() {return "An error";};
	public  static String getHelp() {return "An error";};
	
	
	public void parse (InputStream in){
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		
		try {
			while(br.ready()){
				parse(br.readLine().trim());
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
	}
	
	abstract void parse (String str);
	
	
}
