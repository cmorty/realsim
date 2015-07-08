package de.fau.realsim

import java.io.File
import java.io.BufferedWriter
import java.io.FileWriter
import scala.collection.mutable.Buffer


trait OutputFilter  {
	
	def out(out:Int)
	
	def name:String
	
	def desc:String
	
	def setFile(out:File) 
	
	def out(d:Buffer[Int]) {
		d.foreach(out(_))
	}
	
	
}



class RawOut extends OutputFilter {
	
	var outw:BufferedWriter = null
	
	def setFile(out:File) {
		outw = new BufferedWriter(new FileWriter(out))
	}
	def out(d:Int) {
		outw.append(d.toString() + "\n")
	}
	
	override def out(d:Buffer[Int]) {
		outw.append(d.mkString("", "\n", "\n"))
	}
	
	
	def name = "raw"
	
	def desc = "Raw Values"
	
}


class NullOut extends OutputFilter {
	
	
	def setFile(out:File) {
		
	}
	def out(d:Int) {
		
	}
	
	override def out(d:Buffer[Int]) {
		
	}
	
	
	def name = "null"
	
	def desc = "Null output"
	
}

class RLEO extends OutputFilter {
	
	var outw:BufferedWriter = null
	
	var cnt = 0;
	var last = Int.MaxValue;
	
	def setFile(out:File) {
		outw = new BufferedWriter(new FileWriter(out))
	}
	
	def out(d:Int) {
		if(d != last) {
			if(last != Int.MaxValue) {
				outw.append(last.toString() + ", " + cnt + "\n")
			}
			last = d;
			cnt = 0;
		}
		cnt += 1
	}
	
	def name = "rle"
	
	def desc = "RLE encoded data"
	
}