package de.fau.realsim.cmdhandlers;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import de.fau.realsim.AnalyzedData;
import de.fau.realsim.DataPacket;
import de.fau.realsim.input.PacketInput;
import de.fau.realsim.output.PacketOutput;
import de.fau.realsim.util.ArgumentManager;

public class Convert extends CmdHandler{
	
	public Convert(ArgumentManager am) {
		super(am, "Convert");
	}


	@Override
	public String[] help() {
		ArrayList<String> rv = new ArrayList<String>();	
		rv.add("<Input> <Output>");
		rv.add("Converts from onfile to another");
		rv.add("-start\tStart time");
		rv.add("-end\tEndTime");
		rv.add("-crop\tSet Start and EndTime according to the width ware status of all nodes is available.");
		rv.add(helpPI);
		rv.add(helpPO);
		return rv.toArray(new String[0]);
	}
	
	
	public int main(){

		int start = config.getPropertyAsInt("start", 0);
		int end = config.getPropertyAsInt("end",Integer.MAX_VALUE);
		boolean crop =config.getPropertyAsBoolean("crop", false);
			
		
		if(args.length < 3){
			System.err.print("Inputfile and Outputfile are missing");
			return 1;
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
			return 1;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return 0;
		
		

	}





}
