package de.fau.realsim

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.concurrent.TimeUnit.NANOSECONDS
import scala.collection.mutable.Buffer
import scala.language.implicitConversions
import scopt.OptionParser
import org.reflections.Reflections
import scala.collection.mutable.ArrayBuffer



object RLEdecoder2 {
	
	
	var valdebug = false
	
	var offMinmax=(Int.MaxValue, Int.MinValue)
	var valMinmax=(Int.MaxValue, Int.MinValue)
	var cntmax = Int.MinValue
	var lenstats = Array.ofDim[Double](255)
	var deltastats = Array.ofDim[Double](255)
	var miss:Double = 0

	class HexString(val s: String) {
		def hToI = Integer.parseInt(s, 16)
	}
	implicit def str2hex(str: String): HexString = new HexString(str)

	private def strToId(str: String) = {
		val tok = str.split("\\.")
		//Motes in Cooja are little endian!
		tok(1).toInt * 256 + tok(0).toInt

	}


	def isHex(b: Byte) = b >= '0'.toByte && b <= '0'.toByte + 16

	def chkHex(b: Byte) =  {
			if (!isHex(b)) throw new Exception("Invalid value for Hex: -" + b.toChar + "-") ;	
			b
	}
		
	def toHex(b: Byte) = {chkHex(b); b - '0'.toByte}

	
	def validData(d: String) = {
		d.forall(x => {
					val b = x.toByte;
					b >= '0'.toByte && b < ('A'.toByte + 4 * 10 + 1)
				}
		)
	}
	
	
	
	
	val encstat = Array(0,0,0,0,0,0)
	
	def convEncS(in: Iterator[String], of: OutputFilter) {

		//val sMap = scala.collection.mutable.Map[String, Int]()

		
		
		
		var maxtime = 0

		val statrange = 5

		val stats = Array.fill(2 * statrange + 1)(Array.ofDim[Double](256))

		var linesparsed:Double = 0;
		
		var now = System.nanoTime
		val start = now
		
		//val nid = in.getName.split("_").apply(1)
		
		val startChar = 20
		
		var lv = -1
		
	
		var samp:Double = 0; //Samples decoded
		var rsamp:Double = -1; // Total Remote samples
		var lrsamp:Double = 0 // Last remote samples
		var decBuffer =  ArrayBuffer[Int](1024)
		
		for (l <- in) if (l != "" && in.hasNext) {
			val data = l.substring(startChar);
			
			if(valdebug) println("\n" + data)
			//No whitespaces are good data
			if (data.indexOf(' ') == -1 ) try { 
				linesparsed +=1
				
				var chips = 0
				if(linesparsed % 1000000 == 0) {
					val onow = now
					now = System.nanoTime
					println("Parsed: "  + linesparsed + " Time: " + NANOSECONDS.toMillis(now - onow) + " Total: "+ NANOSECONDS.toMillis(now - start) +
							 " Remote: " + rsamp + " Local: " + samp + " Delta: " + (rsamp - samp))
				}
				
				
				{
					
					decBuffer.clear();
					
					val it = data.map(_.toByte).toIterator
					while (it.hasNext && chips < 40) {
						chips += 1;
						var cnt = 0
						var v = -1;
						val b = it.next
						if(valdebug) print("%02d: %c %03d ".format(chips,b,b))
						if (isHex(b)) { //RLE-Encoding
							if(valdebug) print("\tR ")
							cnt = (toHex(b) << 4) + toHex(it.next)
							v = (toHex(it.next) << 4) + toHex(it.next)
							
							if (cnt > 0xff) {
								throw new Exception("FOOOO: ")

							}
							if(cnt >= 220) {
								println("Error: " + v + " Cnt: " + cnt)
							}
							cntmax = cntmax.max(cnt)
							lenstats(cnt) += 1
							if(cnt == 1 && v != 1 && lv != 1) {
								deltastats(lv - v + 128) += 1
							}
							encstat(0) +=1
						} else { //Single && dual char encoding
							var r = b - 'A'.toByte
							
							//println("" + b.toChar + ": " + r + " = " + r/10 )
							if(valdebug) print("%03d ".format(r))
							val br = 10;
							val sr = 10;
							val dc = 10;
							val dcr = (('z'.toByte - '!'.toByte)/2)

							if (r < br) {
								if(valdebug) print("D1 ")
								encstat(1) +=1
								cnt = 1;
								v = lv - (r + 1);
							} else if ({ r -= br; r } < br) {
								if(valdebug) print("D2 ")
								encstat(2) +=1
								cnt = 1;
								v = lv + r + 1
							} else if ({ r -= br; r } < sr) {
								if(valdebug) print("D3 ")
								encstat(3) +=1
								v = lv - 1
								cnt = r + 2
							} else if ({ r -= sr; r } < sr) {
								if(valdebug) print("D4 ")
								encstat(4) +=1
								v = lv + 1
								cnt = r + 2
							} else if ({ r -= sr; r } < dc) {
								if(valdebug) print("D5 ")
								encstat(5) +=1
								cnt = r + 1
								val c =  it.next.toByte.toInt
								if(c < '!' && c > 'z') {
									throw new Exception("Invalid value: Twobyte -" + b + "-")	
								}
								//if(valdebug) print("%d - ! = %d - %d = %d ".format(c, c-'!', ''))
								val diff = c - dcr - '!'.toByte
								v = lv + diff 
							}	else {
								throw new Exception("Invalid value: -" + b + "-")
							}
							if (lv <= 0) { //Invalidate
								v = -1
							}
						}
						
						if(valdebug) println("\t%3d %3d".format(v, cnt))
						
						if(lv != -1 && v == -1) {
							lv = v;
							throw new Exception("Could not decode line")
						}
						if(v != -1 && v  < 10 ) miss += 1
						
						lv = v;
						for (i <- 0 until cnt){decBuffer += v}
						if(v > 4) valMinmax = (valMinmax._1.min(v), valMinmax._2.max(v))
						
						
					}
					//Without 40 chips something went wrong
					if(chips != 40) throw new Exception("Too few chips: " + chips)
					of.out(decBuffer)
					
					
					//Offset-Monitoring
					samp += decBuffer.length
					val chsamp = ((it.next -  '0'.toByte) << 8) + (toHex(it.next) << 4) + toHex(it.next)
					var dsamp = chsamp - lrsamp
					
					if(rsamp == -1 ) {//First round
						rsamp = samp; 
					} else {
						if(dsamp < 0) dsamp += 0x2000
						rsamp += dsamp
					}
					
					//println("rsamp: "+ rsamp + " samp: " + samp + " delta: " + (rsamp - samp) + " rel: " + dsamp + "  / " + decBuffer.length) 
					val delt = (rsamp - samp).toInt
					offMinmax = (offMinmax._1.min(delt), offMinmax._2.max(delt))
					
					
						
					lrsamp = chsamp
				}
				
			} catch {
				case e: Exception =>
					println(e.getMessage + " ("+ linesparsed  +"): " + l)
					println(e.printStackTrace())
					if(!e.getMessage.startsWith("Invalid value") && e.getMessage != "reached iterator end") throw e
			}
		}	
	}
	
	
	def getIterator(f:File, start:Int, count:Int) = {
		val br = new BufferedReader(new FileReader(f))
		val sb = new StringBuilder
		Iterator.continually(br.readLine()).takeWhile(_ != null).drop(start).take(count)
	}
	
	def getOf = {
		import scala.collection.JavaConversions._
		val reflections = new Reflections("de.fau.realsim") 
		val classes = reflections.getSubTypesOf(classOf[OutputFilter])
		classes.toList.map { x => x.getDeclaredConstructors()(0).newInstance().asInstanceOf[OutputFilter] }
	}

	def main(args: Array[String]): Unit = {

		var infile: File = null
		var outfile: File = null
		var start = 0
		var length = Int.MaxValue

		val fp = new FormulaParser

		val ofs = getOf
		var of:OutputFilter = new RawOut
		
		val parser = new OptionParser[Unit]("RLEdecoder2") {
			head("RLEdecoder2")
			arg[File]("<infile>")
				.text("input file")
				.foreach { infile = _ }
				.validate { f => if (f.exists) success else failure("File " + f.getPath + " not found.") }
			
			arg[String]("<output filter>")
				.text(ofs.map(x => { x.name + ": " + x.desc}).mkString("\n"))
				.validate(x => { if (ofs.map(_.name).contains(x)) success else failure("Unsupported output filter") })
				.foreach(x => { of = ofs.find( _.name == x).get })

			
			opt[Unit]('v', "verbose")
				.optional()
				.text("Be verbose")
				.foreach(x => { valdebug = true })
				

					/*
			arg[File]("<outfile>") 
				.text ("output file")
				.optional()
				.foreach { outfile = _ }
			*/	
				
			arg[Int]("<start offset>") 
				.text ("Start offset in ms. You may use a formula: \"1000*60*10\"")
				.optional()
				.foreach { x => {start =  x}}
			
			arg[Int]("<duration>") 
				.text ("Duration of the sample in ms. You may use a formula: \"1000*60*20\"")
				.optional()
				.foreach { x => {length = x}}
			

		}

		if (!parser.parse(args)) sys.exit(1)


		if (outfile == null) outfile = new File(infile.getName + ".dec")
		of.setFile(outfile)
		
		val it = getIterator(infile, start, length)
		
		convEncS(it, of)
		println("Stats")
		println("Miss " + miss)
		println("Offset: Min/Max " + offMinmax._1 + " / " + offMinmax._2)
		println("Value: Min/Max " + valMinmax._1 + " / " + valMinmax._2)	
		println("CntMAx: " + cntmax)
		val encsum = encstat.sum
		println("Encstats: rls: %d (%2.2f%%)".format(encstat(0), encstat(0).toFloat / encsum * 100)  +
				{(1 to 5).map(x => " D%d: %d (%2.2f%%) ".format(x, encstat(x), encstat(x).toFloat / encsum * 100) ).mkString("") })
		/*
		println("Lenstats")
		for(i <- 0 until 10) {
			if(lenstats(i) != 0) println("len: " + i + " cnt: " + lenstats(i) )
		}
		println("Deltastats")
		for(i <- 0 until 255) {
			if(deltastats(i) != 0) println("delta: " + (i - 128) + " cnt: " + deltastats(i) )
		}
		* 
		*/
	}
}

class RLEdecoder2 {

}