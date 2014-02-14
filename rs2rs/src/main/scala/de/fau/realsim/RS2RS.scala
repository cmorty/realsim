package de.fau.realsim

import java.io.BufferedReader
import java.io.PrintWriter
import java.io.FileReader
import java.io.FileWriter
import scopt.OptionParser
import scopt.OptionParser._
import java.io.File

object RS2RS {

	def conv(in: File, out: File, start: Int, length: Int) {
		val br = new BufferedReader(new FileReader(in))
		val pw = new PrintWriter(new FileWriter(out))
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)

		for (l <- lIt) {
			val sp = l.split(";", 2)
			val tm = sp(0).toInt - start
			val cmd = sp(1)
			if (tm > 0 && tm <= length) {
				pw.println(tm.toString + "" + cmd);
			}
		}
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
			arg[File]("<infile>") text ("input file") foreach { infile = _ }
			arg[File]("<outfile>") text ("output file") foreach { outfile = _ }
			arg[Int]("<start offset>") text ("start offset") foreach { start = _ }
			arg[Int]("<duration>") text ("Duration of the sample") foreach { length = _ }
		}

		if (!parser.parse(args)) sys.exit(1)
		
		conv(infile, outfile, start, length)
		
	}
}

class RS2RS {
	
}