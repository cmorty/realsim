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

import java.util.Collection;
import java.util.Vector;

import org.jdom.Element;

import org.contikios.cooja.radiomediums.AbstractRadioMedium;

// Help class to manage edges
public class RealSimEdge {
	
	public int		src		= -1;
	public int		dst		= -1;
	public double	ratio	= 1.0;								/*
																 * Link success
																 * ratio (per
																 * packet).
																 */
	public double	rssi	= AbstractRadioMedium.SS_STRONG;	/* RSSI */
	public long		delay	= 0;								/*
																 * EXPERIMENTAL:
																 * Propagation
																 * delay (us).
																 */
	public int		lqi		= 105;
	
	public RealSimEdge(Collection<Element> configXML) {
		if (!setConfigXML(configXML)) {
			throw new IllegalArgumentException("RSE: src or dst not correctly set.");
		}
	}
	
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
	
	Collection<Element> getConfigXML() {
		Vector<Element> config = (Vector<Element>)getConfigXMLShort();
		Element el;
		
		config.add(el = new Element("ratio"));
		el.setText( Double.toString(ratio));
		config.add(el = new Element("rssi"));
		el.setText(Double.toString(rssi));
		config.add(el = new Element("delay"));
		el.setText(Long.toString(delay));
		config.add(el = new Element("lqi"));
		el.setText(Integer.toString(lqi));
		return config;
	}
	
	Collection<Element> getConfigXMLShort() {
		Vector<Element> config = new Vector<Element>();
		Element el;
		config.add(el = new Element("src"));
		el.setText(Integer.toString(src));
		config.add(el = new Element("dst"));
		el.setText(Integer.toString(dst));
		return config;
	}
	
	public boolean setConfigXML(Collection<Element> configXML) {
		for (Element element : configXML) {
			String name = element.getName().toLowerCase();
			String value = element.getText();
			
			if (name.equals("src")) {
				src = Integer.parseInt(value);
			}
			if (name.equals("dst")) {
				dst = Integer.parseInt(value);
			}
			if (name.equals("ratio")) {
				ratio = Double.parseDouble(value);
			}
			if (name.equals("rssi")) {
				rssi = Double.parseDouble(value);
			}
			if (name.equals("delay")) {
				delay = Long.parseLong(value);
			}
			if (name.equals("lqi")) {
				lqi = Integer.parseInt(value);
			}
			
		}
		if (src == -1 || dst == -1)
			return false;
		return true;
	}
	
	public boolean equals(RealSimEdge e) {
		return (this.src == e.src && this.dst == e.dst) ? true : false;
	}
	
	@Override
	public int hashCode() {
		return (int) ((src * 255* 255 + dst) *31 + (new Double(ratio * 1000).intValue()) + rssi + lqi *100);
	}
	// Todo
	// if (ratio <= 0.0 || ratio > 1.0 || rssi > 90 || rssi <= 0 || lqi > 110 ||
	// lqi <= 0) {
}
