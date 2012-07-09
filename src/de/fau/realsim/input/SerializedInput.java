package de.fau.realsim.input;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import de.fau.realsim.DataPacket;
import de.fau.realsim.DataPacketHandler;

public class SerializedInput extends PacketInput{
	public SerializedInput(DataPacketHandler dph) {
		super(dph);
		// TODO Auto-generated constructor stub
	}

	public void parse (InputStream in){
		ObjectInputStream o;
		try {
			o = new ObjectInputStream(in);
			DataPacket[] pkts = (DataPacket[]) o.readObject();
			for(DataPacket pkt : pkts){
				dph.handleDataPaket(pkt);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		
		
	}

	@Override
	void parse(String str) {
		// Not supported
		// TODO Throw exception
	}
}
