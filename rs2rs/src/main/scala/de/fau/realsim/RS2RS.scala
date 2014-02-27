package de.fau.realsim

import java.io.BufferedReader
import java.io.PrintWriter
import java.io.FileReader
import java.io.FileWriter
import scopt.OptionParser
import scopt.OptionParser._
import java.io.File
import scala.collection.mutable.Buffer

class event(val time:Int, val cmd:List[String]){
	
	def  overwrites(e:event):Boolean = {
		if(e.time >= time) (cmd(0), e.cmd(0)) match {
			case ("addnode" | "rmnode", "addnode" | "rmnode") =>  e.cmd(1) == cmd(1)
			case ("setedge" | "rmedge", "setedge" | "rmedge") =>  e.cmd(1) == cmd(1) && e.cmd(2) == cmd(2) 
			case ("baserssi", "baserssi") => e.cmd(1) == cmd(1)
			case _ => false
		} else {
			false
		}
		
	}
}


object RS2RS {

	var backlog = List[event]()
	
	
	def conv(in: File, out: File, start: Int, length: Int) {
		val br = new BufferedReader(new FileReader(in))
		val pw = new PrintWriter(new FileWriter(out))
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)

		for (l <- lIt) {
			val sp = l.split(";", 2)
			val tm = sp(0).toInt - start
			val cmd = sp(1)
			if(tm < 2) { // 0 and 1 are reserved for initialisation
				val ne = new event(tm, cmd.split(";").toList)
				if(!backlog.exists(_.overwrites(ne))) {
					backlog = backlog.filterNot(ne.overwrites(_))
					backlog = ne :: backlog 
				}
				//println(backlog.map(x => (x.time.toString :: x.cmd).mkString(";")).mkString("\n"))
			} else if (tm <= length) {
				pw.println(tm.toString + ";" + cmd);
			}
		}
		//Add start-events
		println(backlog.map(x => (x.time.toString :: x.cmd).mkString(";")).mkString("\n"))
		
		pw.print(backlog.map(x => ({
			if(x.cmd(0) == "addnode") "0" else "1"
			} :: x.cmd).mkString(";")).mkString("\n"))
		
		
		br.close
		pw.close
	}

	def main(args: Array[String]): Unit = {

		var infile: File = null
		var outfile: File = null
		var start = 0
		var length = 0

		val parser = new OptionParser[Unit]("RS2RS") {
			head("RealSim2RealSim")
			arg[File]("<infile>") 
				.text ("input file") 
				.foreach { infile = _ }
				.validate { f => if(f.exists) success else failure("File " + f.getPath + "not found.")}
			
			arg[File]("<outfile>") 
				.text ("output file") 
				.foreach { outfile = _ }
				.validate { f => if(f.exists) success else failure("File " + f.getPath + "not found.")}
				
			arg[Int]("<start offset>") text ("start offset") foreach { start = _ }
			arg[Int]("<duration>") text ("Duration of the sample") foreach { length = _ }
		}

		if (!parser.parse(args)) sys.exit(1)
		
		conv(infile, outfile, start, length)
		
	}
}

class RS2RS {
	
}