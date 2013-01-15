package de.fau.realsim.output;

import java.io.OutputStream;

import de.fau.realsim.AnalyzedData;

public interface PacketOutput {
	public boolean output(AnalyzedData anl, OutputStream os);
	public boolean output(AnalyzedData anl, OutputStream os, int start, int end);
}
