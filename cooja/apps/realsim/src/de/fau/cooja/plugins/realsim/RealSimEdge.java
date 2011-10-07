/**
 * Copyright (c) 2011, Simon Böhm
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE 
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 * @author Simon Böhm <mail@boehm-simon.de>
 * 
 */


package de.fau.cooja.plugins.realsim;

import se.sics.cooja.radiomediums.AbstractRadioMedium;

// Help class to manage edges
public class RealSimEdge {
	
	public int	src;
	public int	dst;
	public double ratio = 1.0; /* Link success ratio (per packet). */
	public double rssi = AbstractRadioMedium.SS_STRONG; /* RSSI */
	public long delay = 0; /* EXPERIMENTAL: Propagation delay (us). */
	public int lqi = 105;
	
	public RealSimEdge(int src, int dst) {
		this.src = src;
		this.dst = dst;
	}
	
	public RealSimEdge(int src, int dst, double ratio, double rssi, long delay, int lqi) {
		this.src = src;
		this.dst = dst;
		this.ratio = ratio;
		this.rssi = rssi;
		this.delay = delay;
		this.lqi = lqi;
	}

	
	public boolean equals(RealSimEdge e) {
		return (this.src == e.src && this.dst == e.dst) ? true : false;
	}
	
	
	//Todo
	// if (ratio <= 0.0 || ratio > 1.0 || rssi > 90 || rssi <= 0 || lqi > 110 || lqi <= 0) {
}
