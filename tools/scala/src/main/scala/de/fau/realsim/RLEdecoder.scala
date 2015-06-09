package de.fau.realsim

import java.io.BufferedReader
import java.io.PrintWriter
import java.io.FileReader
import java.io.FileWriter
import scopt.OptionParser
import scopt.OptionParser._
import java.io.File
import scala.collection.mutable.UnrolledBuffer
import scala.collection.mutable.HashMap
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import scala.collection.convert.Wrappers.ToIteratorWrapper
import java.util.concurrent.TimeUnit.NANOSECONDS

import Data._

object RLEdecoder {

	class HexString(val s: String) {
		def hToI = Integer.parseInt(s, 16)
	}
	implicit def str2hex(str: String): HexString = new HexString(str)

	private def strToId(str: String) = {
		val tok = str.split("\\.")
		//Motes in Cooja are little endian!
		tok(1).toInt * 256 + tok(0).toInt

	}

	var lp = 1;

	val ofMap = Map[String, String => Unit](
		"tv" -> (out => {
			
			var now = System.nanoTime
			var lines = 0;
			for ((nid, ds) <- dMap) {
				val outw = new FileWriter(out + "." + nid + ".tv")
				var pos = 0;
				for (d <- ds) {
					val tm = pos.toDouble * 128  / 1000000
					outw.write(tm.toString) 
					outw.write(", ")
					outw.write(d.value.toString)
					outw.write("\n")
					pos += d.dur
					lines += 1 
					if(lines % 1000000 == 0) {
						val onow = now
						now = System.nanoTime
						println("Output: "  + pos + " Time: " + NANOSECONDS.toMillis(now - onow))
				}
				}
				outw.close()
			}
		}),
		"dsbin" -> (out => {
			for ((nid, ds) <- dMap) {
				val outw = new FileOutputStream(out + "." + nid + ".ds")
				for (d <- ds) {
					outw.write(Array.fill(d.dur)(d.value.toByte))
				}
				outw.close()
			}
		}),
		"dsasc" -> (out => {
			val outw = new PrintWriter(new FileOutputStream(out + ".ass"))
			outw.println(dMap.keys.mkString(", "))
			class Tdat(var dur: Int, var value: Int);
			val RLEdec = List.fill(dMap.size)(new Tdat(0, 0))
			val iterators = dMap.map(_._2.toIterator).toList
			var cnt = true;
			while (cnt) {
				for (i <- 0 to dMap.size - 1) {
					if (RLEdec(i).dur == 0) if (iterators(i).hasNext) {
						val v = iterators(i).next
						RLEdec(i).dur = v.dur
						RLEdec(i).value = v.value
					} else {
						cnt = false
					}
				}
				if (cnt != false) {
					outw.println(RLEdec.map(_.value).mkString(",\t"))
					RLEdec.foreach(_.dur -= 1)
				}
			}
			outw.close()
		}),
		"dsascgz" -> (out => {
			for ((nid, ds) <- dMap) {
				val outw = new PrintWriter(new GZIPOutputStream(new FileOutputStream(out + "." + nid + ".ds.gz")))
				for (d <- ds) {
					outw.print(Array.fill(d.dur)(d.value + "\n"))
				}
				outw.close()
			}
		}),
		"nrle" -> (out => {
			val outw = new PrintWriter(new FileWriter(out + ".nrle"))
			outw.println(List("node", "value", "length").mkString(",\t"))
			for ((nid, ds) <- dMap) {
				for (d <- ds) {
					outw.println(List(nid, d.value, d.dur).mkString(",\t"))
				}
			}
			outw.close()
		})
	)



	//class Data(val value: Int, val dur: Int)
	val dMap = scala.collection.mutable.Map[Int, UnrolledBuffer[Int]]()

	def convRLE(in: File, out: String) {

		val br = new BufferedReader(new FileReader(in))
		val sb = new StringBuilder
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)
		val sMap = scala.collection.mutable.Map[Int, Data]()

		var maxtime = 0

		for (l <- lIt) if (l != "") {
			val sp = l.split(' ')
			if (sp(1)(0) == 'R') {
				val nid = sp(0).split(':')(3).tail.tail.hToI
				val idata = sp(1).tail.filter(Character.digit(_, 16) != -1)

				if (idata.length() % 4 != 0) {
					println("-- " + l)
				}

				val data = idata.grouped(2).map(x => {
					try {
						x.mkString.hToI
					} catch {
						case t: Throwable =>
							println(l)
							throw t
					}
				}).grouped(2).toList
				for (ds <- data) {
					val value = ds(0)
					val duration = ds(1)
					val cv = sMap.getOrElseUpdate(nid, Data(value, 0))
					if (Math.abs(cv.value - value) < lp) {
						sMap += nid -> Data(value, cv.dur + duration)
					} else {
						dMap.getOrElseUpdate(nid, UnrolledBuffer[Int]()) += cv
						sMap += nid -> Data(value, duration)

					}
				}
			}
		}
	}

	def convEnc(in: File, out: String) {

		val br = new BufferedReader(new FileReader(in))
		val sb = new StringBuilder
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)
		val sMap = scala.collection.mutable.Map[Int, Data]()

		var maxtime = 0

		def isHex(b: Byte) = {
			b >= '0'.toByte && b <= '0'.toByte + 16
		}

		def chkHex(b: Byte) = {
			if (!isHex(b)) throw new Exception("Invalid value")
			b
		}

		def toHex(b: Byte) = b - '0'.toByte

		val statrange = 5

		val stats = Array.fill(2 * statrange + 1)(Array.ofDim[Int](256))

		for (l <- lIt) if (l != "") {
			val sp = l.split(' ')
			if (sp.length == 3) try {

				val nid = sp(1).split(':')(2).tail.tail.hToI

				val data = sp(2);

				if (data.forall(x => {
					val b = x.toByte;
					b >= '0'.toByte && b < ('A'.toByte + 4 * 10 + 1)
				})) {

					val it = data.map(_.toByte).toIterator
					while (it.hasNext) {

						var cnt = 0
						var v = 0;
						val b = it.next
						if (isHex(b)) {
							cnt = (toHex(b) << 4) + toHex(chkHex(it.next))
							if (cnt > 0xff) {
								throw new Exception("FOOOO: ")

							}
							v = (chkHex(it.next) << 4) + chkHex(it.next)
						} else if (sMap.contains(nid)) { //Single char encoding
							val lv = sMap.get(nid).get.value
							val r = b - 'A'.toByte
							//println("" + b.toChar + ": " + r + " = " + r/10 )

							v = lv + ((r / 10) match {
								case 0 => 2
								case 1 => 1
								case 2 => -1
								case 3 => -2
							})
							cnt = r % 10 + 1
						}

						//STATS
						if (sMap.contains(nid)) {
							val lv = sMap.get(nid).get.value
							val delt = v - lv;
							if (-statrange <= delt && delt <= statrange) {
								stats(delt + statrange)(cnt) += 1;
							}

						}

						if (v == 1 || v == 0) {
							println("Node: " + nid + " Warning : " + v)
						}

						val cv = sMap.getOrElseUpdate(nid, Data(v, 0))
						if (Math.abs(cv.value - v) < lp) {
							sMap += nid -> Data(v, cv.dur + cnt)
						} else {
							dMap.getOrElseUpdate(nid, UnrolledBuffer[Int]()) += cv
							sMap += nid -> Data(v, cnt)
						}

						//

					}

				}
			} catch {
				case e: Exception =>
					println(l)
					if (e.getMessage != "Invalid value" && e.getMessage != "reached iterator end") throw e
			}
		}
		for (off <- 0 to statrange * 2) {
			for (i <- 0 to 255) {
				if (stats(off)(i) != 0) {
					println("" + (off - statrange) + " " + "%02d".format(i) + "\t" + stats(off)(i))
				}
			}
		}
	}

	def convEnc2(in: File, out: String) {

		val br = new BufferedReader(new FileReader(in))
		val sb = new StringBuilder
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)
		val sMap = scala.collection.mutable.Map[Int, Int]()

		var maxtime = 0

		def isHex(b: Byte) = {
			b >= '0'.toByte && b <= '0'.toByte + 16
		}

		def chkHex(b: Byte) = {
			if (!isHex(b)) throw new Exception("Invalid value for Hex: -" + b.toChar + "-")
			b
		}

		def toHex(b: Byte) = {chkHex(b); b - '0'.toByte}

		val statrange = 5

		val stats = Array.fill(2 * statrange + 1)(Array.ofDim[Int](256))

		var linesparsed = 0;
		
		var now = System.nanoTime
		val start = now
		
		for (l <- lIt) if (l != "") {
			val data = l.substring(35);
			if (data.indexOf(' ') == -1 ) try {
				linesparsed +=1
				if(linesparsed % 1000000 == 0) {
					val onow = now
					now = System.nanoTime
					println("Parsed: "  + linesparsed + " Time: " + NANOSECONDS.toMillis(now - onow) + " Total: "+ NANOSECONDS.toMillis(now - start))
				}
				val nid = l.substring(30,30+4).hToI

				

				if (data.forall(x => {
					val b = x.toByte;
					b >= '0'.toByte && b < ('A'.toByte + 4 * 10 + 1)
				})) {

					val it = data.map(_.toByte).toIterator
					while (it.hasNext) {

						var cnt = 0
						var v = -1;
						val b = it.next
						if (isHex(b)) {
							cnt = (toHex(b) << 4) + toHex(it.next)
							if (cnt > 0xff) {
								throw new Exception("FOOOO: ")

							}
							v = (toHex(it.next) << 4) + toHex(it.next)
						} else if (sMap.contains(nid)) { //Single char encoding
							val lv = sMap.get(nid).get.value
							var r = b - 'A'.toByte
							//println("" + b.toChar + ": " + r + " = " + r/10 )

							val br = 10;
							val sr = 10;

							if (r < br) {
								cnt = 1;
								v = lv - (r + 1);
							} else if ({ r -= br; r } < br) {
								cnt = 1;
								v = lv + r + 1
							} else if ({ r -= br; r } < sr) {
								v = lv - 1
								cnt = r + 2
							} else if ({ r -= sr; r } < sr) {
								v = lv + 1
								cnt = r + 2
							} else {
								throw new Exception("Invalid value: -" + b + "-")
							}
						}

						if(v != -1) {
							//STATS
							if (sMap.contains(nid)) {
								val lv = sMap.get(nid).get.value
								val delt = v - lv;
								if (-statrange <= delt && delt <= statrange) {
									stats(delt + statrange)(cnt) += 1;
								}
	
							}
							//println("VAL: " + nid + " "  + v)
							
							try {
								val cv = sMap.getOrElseUpdate(nid, Data(v, 0))
								if (Math.abs(cv.value - v) < lp) {
									sMap += nid -> Data(v, cv.dur + cnt)
								} else {
									dMap.getOrElseUpdate(nid, UnrolledBuffer[Int]()) += cv
									sMap += nid -> Data(v, cnt)
								}
							} catch {
								case e: Exception =>
									if(e.getMessage.startsWith("Invalid Data-value")) {
										//println("DDDD: " + dMap.get(nid).get.length)
									}
										
									throw e
							}
						}

					}

				}
			} catch {
				case e: Exception =>
					println(e.getMessage + ": " + l)
					if (!e.getMessage.startsWith("Invalid value") && e.getMessage != "reached iterator end") throw e
			}
		}
		for (off <- 0 to statrange * 2) {
			for (i <- 0 to 255) {
				if (stats(off)(i) != 0) {
					println("" + (off - statrange) + " " + "%02d".format(i) + "\t" + stats(off)(i))
				}
			}
		}
	}
	
	def convEnc3(in: File, out: String) {

		val br = new BufferedReader(new FileReader(in))
		val sb = new StringBuilder
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)
		val sMap = scala.collection.mutable.Map[String, Int]()

		var maxtime = 0

		def isHex(b: Byte) = {
			b >= '0'.toByte && b <= '0'.toByte + 16
		}

		def chkHex(b: Byte) = {
			if (!isHex(b)) throw new Exception("Invalid value for Hex: -" + b.toChar + "-")
			b
		}

		def toHex(b: Byte) = {chkHex(b); b - '0'.toByte}

		val statrange = 5

		val stats = Array.fill(2 * statrange + 1)(Array.ofDim[Int](256))

		var linesparsed = 0;
		
		var now = System.nanoTime
		val start = now
		
		val nid = in.getName.split("_").apply(1)
		
		for (l <- lIt) if (l != "") {
			val data = l.substring(35);
			if (data.indexOf(' ') == -1 ) try {
				linesparsed +=1
				if(linesparsed % 1000000 == 0) {
					val onow = now
					now = System.nanoTime
					println("Parsed: "  + linesparsed + " Time: " + NANOSECONDS.toMillis(now - onow) + " Total: "+ NANOSECONDS.toMillis(now - start))
				}
				

				

				if (data.forall(x => {
					val b = x.toByte;
					b >= '0'.toByte && b < ('A'.toByte + 4 * 10 + 1)
				})) {

					val it = data.map(_.toByte).toIterator
					while (it.hasNext) {

						var cnt = 0
						var v = -1;
						val b = it.next
						if (isHex(b)) {
							cnt = (toHex(b) << 4) + toHex(it.next)
							if (cnt > 0xff) {
								throw new Exception("FOOOO: ")

							}
							v = (toHex(it.next) << 4) + toHex(it.next)
						} else if (sMap.contains(nid)) { //Single char encoding
							val lv = sMap.get(nid).get.value
							var r = b - 'A'.toByte
							//println("" + b.toChar + ": " + r + " = " + r/10 )

							val br = 10;
							val sr = 10;

							if (r < br) {
								cnt = 1;
								v = lv - (r + 1);
							} else if ({ r -= br; r } < br) {
								cnt = 1;
								v = lv + r + 1
							} else if ({ r -= br; r } < sr) {
								v = lv - 1
								cnt = r + 2
							} else if ({ r -= sr; r } < sr) {
								v = lv + 1
								cnt = r + 2
							} else {
								throw new Exception("Invalid value: -" + b + "-")
							}
						}

						if(v != -1) {
							//STATS
							if (sMap.contains(nid)) {
								val lv = sMap.get(nid).get.value
								val delt = v - lv;
								if (-statrange <= delt && delt <= statrange) {
									stats(delt + statrange)(cnt) += 1;
								}
	
							}
							//println("VAL: " + nid + " "  + v)
							
							try {
								val cv = sMap.getOrElseUpdate(nid, Data(v, 0))
								if (Math.abs(cv.value - v) < lp) {
									sMap += nid -> Data(v, cv.dur + cnt)
								} else {
									dMap.getOrElseUpdate(nid, UnrolledBuffer[Int]()) += cv
									sMap += nid -> Data(v, cnt)
								}
							} catch {
								case e: Exception =>
									if(e.getMessage.startsWith("Invalid Data-value")) {
										//println("DDDD: " + dMap.get(nid).get.length)
									}
										
									throw e
							}
						}

					}

				}
			} catch {
				case e: Exception =>
					println(e.getMessage + ": " + l)
					if (!e.getMessage.startsWith("Invalid value") && e.getMessage != "reached iterator end") throw e
			}
		}
		for (off <- 0 to statrange * 2) {
			for (i <- 0 to 255) {
				if (stats(off)(i) != 0) {
					println("" + (off - statrange) + " " + "%02d".format(i) + "\t" + stats(off)(i))
				}
			}
		}
	}
	

	def main(args: Array[String]): Unit = {

		var infile: File = null
		var outfile: File = null
		var start = 0
		var length = 0

		val fp = new FormulaParser

		var of = ofMap("tv")

		val parser = new OptionParser[Unit]("RLEdecoder") {
			head("RLEdecoder")
			arg[File]("<infile>")
				.text("input file")
				.foreach { infile = _ }
				.validate { f => if (f.exists) success else failure("File " + f.getPath + " not found.") }

			arg[String]("output filter")
				.optional()
				.text("tv:time/val ds:data stream")
				.validate(x => { if (ofMap.contains(x)) success else failure("Unsupported output filter") })
				.foreach(x => { of = ofMap(x) })

			opt[Int]("lowpass")
				.optional()
				.text("Add low pass filter to remove small changes")
				.foreach(x => { lp = x })
				.abbr("lp")

			/*		
			arg[File]("<outfile>") 
				.text ("output file")
				.optional()
				.foreach { outfile = _ }
				
				
			arg[String]("<start offset>") 
				.text ("Start offset in ms. You may use a formula: \"1000*60*10\"")
				.optional()
				.foreach { x => {start =  fp.evaluate(x).toInt}}
			
			arg[String]("<duration>") 
				.text ("Duration of the sample in ms. You may use a formula: \"1000*60*20\"")
				.optional()
				.foreach { x => {length = fp.evaluate(x).toInt}}
			*/

		}

		if (!parser.parse(args)) sys.exit(1)

		println("LP = " + lp)

		if (outfile == null) outfile = new File(infile.getName + ".dec")

		convEnc2(infile, outfile.toString)
		of(outfile.toString)

	}
}

class RLEdecoder {

}