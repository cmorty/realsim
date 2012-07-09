package de.fau.realsim.input;

import de.fau.realsim.DataPacketHandler;

public class SerialdumpNetstatParser extends NetstatParser {

	public SerialdumpNetstatParser(DataPacketHandler dph) {
		super(dph);
	}
	
	public void parse (String data){
		String lines[] = data.split("\\r?\\n");
		for(String line: lines){
			String[] el = line.split(" ",2);
			super.parse(el[1]);
		}
	}
	
	
}
