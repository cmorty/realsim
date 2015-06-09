package de.fau.realsim

import java.io.BufferedReader
import java.io.PrintWriter
import java.io.FileReader
import java.io.FileWriter
import scopt.OptionParser
import scopt.OptionParser._
import java.io.File
import scala.collection.mutable.Buffer

class event(val time:Int, val cmd:Array[String]){
	
	def  overwrites(e:event):Boolean = {
		if(e.time >= time) (cmd(0), e.cmd(0)) match {
			case ("addnode" | "rmnode", "addnode" | "rmnode") =>  e.cmd(1) == cmd(1)
			case ("setedge" | "rmedge", "setedge" | "rmedge") =>  e.cmd(1) == cmd(1) && e.cmd(2) == cmd(2) 
			case ("setbaserssi", "setbaserssi") => e.cmd(1) == cmd(1)
			case _ => false
		} else {
			false
		}
		
	}
	
	override
	def toString() = {
		var rv = new StringBuilder();
		cmd(0)  match {
			case "addnode" => rv ++= "0;"
			case "motetype" => 
			case _ => rv ++= "1;"
		}
		rv ++= cmd.mkString(";")
		rv.toString
	}
}


object RS2RS {

	var backlog = Buffer[event]()
	
	
	def conv(in: File, out: File, start: Int, length: Int) {
		val br = new BufferedReader(new FileReader(in))
		val sb = new StringBuilder
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)

		for (l <- lIt) if(l != ""){
			val sp = l.split(";", 2)
			if(sp(0) == "motetype") {
				backlog += new event(-1, sp) 
			} else {
				val tm = sp(0).toInt - start
				val cmd = sp(1)
				if(tm < 2) { // 0 and 1 are reserved for initialisation
					val ne = new event(tm, cmd.split(";"))
					if(!backlog.exists(_.overwrites(ne))) {
						backlog = backlog.filterNot(ne.overwrites(_))
						backlog += ne  
					}
					//println(backlog.map(x => (x.time.toString :: x.cmd).mkString(";")).mkString("\n"))
				} else if (tm <= length) {
					sb ++= tm.toString + ";" + cmd + "\n"
				}
			}
		}
		
		val pw = new PrintWriter(new FileWriter(out))
		
		//Add start-events
		//println(backlog.map(x => (x.time.toString :: x.cmd.toList).mkString(";")).mkString("\n"))
		
		println(backlog.map(_.toString).mkString("\n"))
		pw.println(backlog.map(_.toString).mkString("\n"))
		pw.print(sb)
		
		
		br.close
		pw.close
	}

	def main(args: Array[String]): Unit = {

		var infile: File = null
		var outfile: File = null
		var start = 0
		var length = 0

		val fp = new FormulaParser
		
		val parser = new OptionParser[Unit]("RS2RS") {
			head("RealSim2RealSim")
			arg[File]("<infile>") 
				.text ("input file") 
				.foreach { infile = _ }
				.validate { f => if(f.exists) success else failure("File " + f.getPath + " not found.")}
			
			arg[File]("<outfile>") 
				.text ("output file") 
				.foreach { outfile = _ }
				
			arg[String]("<start offset>") text ("Start offset in ms. You may use a formula: \"1000*60*10\"") foreach { x => {start =  fp.evaluate(x).toInt}}
			arg[String]("<duration>") text ("Duration of the sample in ms. You may use a formula: \"1000*60*20\"") foreach { x => {length = fp.evaluate(x).toInt}}
			
		}

		if (!parser.parse(args)) sys.exit(1)
		
		conv(infile, outfile, start, length)
		
	}
}

class RS2RS {
	
}