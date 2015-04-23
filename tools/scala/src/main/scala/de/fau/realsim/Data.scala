package de.fau.realsim
import scala.language.implicitConversions


class Data(val v: Int) extends AnyVal {
	def value = v & 0xFF;
	def dur = v >> 8;
}

object Data {
	def apply(value: Int, dur: Int) = {
		if (value < 0 || value > 0xFF) throw new Exception("Invalid Data-value: " + value)
		if (dur < 0 || dur > 0xFFFFF) throw new Exception("Invalid Data-duration: " + dur) //7 Bit buffer
		new Data(dur << 8 | (value & 0xFF))
	}
	
	implicit def intToData (v:Int) = new Data(v)
	implicit def dataToInt (v:Data) = v.v
	
}