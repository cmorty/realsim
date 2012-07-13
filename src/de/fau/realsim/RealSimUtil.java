package de.fau.realsim;

public final class RealSimUtil {
	public static String idToStringInt(int id){
		return String.format("%d.%d", id%0x100, id / 0x100 );
	}
	
	public static String idToStringHex(int id){
		return String.format("%x.%x", id%0x100, id / 0x100 );
	}
}
