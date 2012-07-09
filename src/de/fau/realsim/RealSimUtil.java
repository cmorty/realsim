package de.fau.realsim;

public final class RealSimUtil {
	public static String idToStringInt(int id){
		return String.format("%d.%d", id%255, id / 255 );
	}
}
