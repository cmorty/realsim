

package de.fau.realsim

import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import scala.collection.mutable.HashMap
import java.io.FileWriter
import scala.Array.canBuildFrom
import scopt.mutable.OptionParser
import scopt.mutable.OptionParser._
import java.util.Date
import java.io.PrintWriter

object Log2RealSim {

	val last = HashMap[Tuple2[Int, Int], Long]()
	val dup = HashMap[Int, Array[String]]()
	
	val dp=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	var nacnt = 0
	var rem = 0
	var cout = 0;
	//Output for realsim
	var rsOut:PrintWriter = null
	//Output for r
	var connOut:PrintWriter = null
	var rssiOut:PrintWriter = null
	//Dates to filter
	var fStartDate:Date = new Date(0)
	var fEndDate:Date = new Date(Long.MaxValue)
	//Date of the collected data
	var startDate:Long = 0
	var endDate:Long = 0
	val addnode =  scala.collection.mutable.Set[Int]()
	
	def idToString(id:Int) = ("%d.%d").format( id%0x100, id / 0x100 )
	

	
	def parseLine(l:String) {
		val el = l.split(" ")
		var dst = 0 
		var src = 0
		var rssi =0
		var lqi =0 
		var rcv = 0
		var loss = 0
		var numb:Array[Int] = null
		var d:Date = null
		
		
					/*
			printf("R: %x %x %x %x %x %x %x\n",
				*(uint16_t*)&(rimeaddr_node_addr),
				*(uint16_t*)&(n->addr),
				n->rssi /  n->recv_count,
				n->lqi /  n->recv_count,
				n->recv_count,
				BEACONS_PER_PERIODE  -  n->recv_count,
				n->dup_count);*/
		
		/*	printf("RE2: %i %i %i %i %i %i %i %i\n",
				*(uint16_t*)&(rimeaddr_node_addr),
				*(uint16_t*)&(n->addr),
				n->last_seqno / BEACONS_PER_PERIODE,
				n->rssi /  n->recv_count,
				n->lqi /  n->recv_count,
				n->recv_count,
				BEACONS_PER_PERIODE  -  n->recv_count,
				n->dup_count);*/
		
		/*	printf("RE: %x %x %x %x %x %x %x %x\n",
				*(uint16_t*)&(rimeaddr_node_addr),
				*(uint16_t*)&(n->addr),
				n->last_seqno / BEACONS_PER_PERIODE,
				n->rssi /  n->recv_count,
				n->lqi /  n->recv_count,
				n->recv_count,
				BEACONS_PER_PERIODE  -  n->recv_count,
				n->dup_count);*/
		
		/*printf("bRSSI: %i %i %" PRIi32 " %" PRIu16 " %i\n", b.brssi_min, b.brssi_max, b.brssi_sum, b.counter, b.brssi_avg);*/
		
		//Check number of elements
		if(el.length < 2 ) return
		
		//Test Date
		d = dp.parse(el(0));

		//Ignore unneeded
		
		if(d.before(fStartDate)) return;			
		if(d.after(fEndDate)) return;
		
		if(startDate == 0) startDate = d.getTime
		
		endDate = d.getTime
		val rsTime = d.getTime - startDate; 
		
		
		def isT(suf:String*):Boolean = {
			suf.exists(el(1).endsWith(_))
		}
		
		if(isT("bRSSI:")) {
			numb = try{
				 el.tail.tail.map(java.lang.Integer.parseInt(_))
			} catch {
				case e:Throwable => 
					println("Failed to pase " + l )
					return
			}
			val nd = idToString(numb(0))
			val min = numb(1).toInt
			val max = numb(2).toInt
			val sum = numb(3).toInt
			val ctr = numb(4).toInt
			rsOut.println("%d;setbaserssi;%s;%i".format(rsTime, nd, (sum.toFloat/ctr).round.toInt))
			rssiOut.println(List(dp.format(d), nd, min, max, sum, ctr).mkString(" "))
			cout += 1
			return
		}
		
		
		//Edge-Stuff starts here

		if(isT("R:", "RE:", "DIS:")){
			numb = try{
				 el.tail.tail.map(java.lang.Integer.parseInt(_, 16))
			} catch {
				case e:Throwable => 
					println("Failed to pase " + l )
					return
			}
		} else if(isT("RE2:", "DIS2:")){
			numb = try{
				 el.tail.tail.map(java.lang.Integer.parseInt(_))
			} catch {
				case e:Throwable => 
					println("Failed to pase " + l )
					return
			}
		} 
		else return;
		
		
		if(isT("R:")){			
			if(el.length < 7) return
			dst = numb(0)
			src = numb(1)
			rssi = numb(2);
			lqi = numb(3);
			rcv = numb(4);
			loss = numb(5);
		} else if(isT("RE:", "RE2:")){
			if(el.length < 8) return
			dst = numb(0)
			src = numb(1)
			if(isT("RE:")) {
				//Substract 55
				var v = numb(3) - 55
				//Fix underrun
				if(v < 0) v += 255
				//Fix lost sign
				if(v > 128) v -= 255
				//Offset
				rssi = v - 45
			} else {
				rssi = numb(3) - 45
			}
			lqi = numb(4);
			rcv = numb(5);
			loss = numb(6);	
			
		}  else if(isT("DIS:", "DIS2:")){			
			dst = numb(0)
			src = numb(1)
		} else return
		
		if(src == 0 ||dst == 0) return		

		
		//Check for dups by broken liner
		dup.get(numb(0)) match{
			case Some(s) =>
				if(el.tail.sameElements(s)){
					rem += 1
					return   //This is an artifact
				} else {
				//	println("==\n=" +el.tail.mkString(" ") + "=\n="  + s.mkString(" ") + "=")
				}
			case None =>
		}
		

		//Add Nodes
		if(addnode.add(src)){ rsOut.println("0;addnode;" + idToString(src)) ; cout+=1} 
		if(addnode.add(dst)){ rsOut.println("0;addnode;" + idToString(dst)); cout +=1}
		
		//output
		if(List("R:", "RE:", "RE2:").exists(el(1).endsWith(_))){
			//Check for connection loss
			val ds = Tuple2(numb(0), numb(1))
			last.get(ds) match {
				case Some(r) => 
					val dt = d.getTime   - r
					if(dt >= 160 * 1000){
						val _rsTime = r - startDate + 160 * 1000
						nacnt += 1
						//rOut.println(List(dp.format(new Date(dt + 160 * 1000)), dst, src, "NA","NA","NA","NA").mkString(" "))
						rsOut.println("%d;rmedge;%s;%s".format(_rsTime, idToString(src), idToString(dst)))
						cout +=1;
					} 	
				case None =>
			}
			connOut.println(List(dp.format(d), dst, src, rssi, lqi, rcv, loss).mkString(" "))
			rsOut.println("%d;setedge;%s;%s;%f;%d;%d".format(rsTime, idToString(src), idToString(dst), rcv.toFloat/(rcv + loss)  * 100, rssi, lqi))
			cout += 1
			last.put(ds, d.getTime )
		} else if(List("DIS:", "DIS2:").exists(el(1).endsWith(_))){
			connOut.println(List(dp.format(d), dst, src, "NA","NA","NA","NA").mkString(" "))
			rsOut.println("%d;rmedge;%s;%s".format(rsTime, idToString(src), idToString(dst)))
			cout += 1
		}
		
		dup.put(dst, el.tail)
		
	}
	
	
	def main(args: Array[String]): Unit = {
		var infile:String = ""
		var outfile:String = ""
		
		
		val parser = new OptionParser("Log2RealSim") {
		  arg("<infile>", "<infile> input file", { v: String => infile = v })
		  argOpt("[<outfile>]", "<outfile> output file (.rs and .r will be attached ) ", { v: String => outfile = v })
		  opt("start", "When to start parsing in yyyy-MM-ddTHH:mm:ss",  {v: String => fStartDate = dp.parse(v)})
		  opt("end", "When to stop parsing in yyyy-MM-ddTHH:mm:ss",  {v: String => fEndDate = dp.parse(v)})
		  
		  
		  // arglist("<file>...", "arglist allows variable number of arguments",
		  //   { v: String => config.files = (v :: config.files).reverse })
		}
		
		if(!parser.parse(args)) sys.exit(1)
		
		if(outfile == "") outfile = infile 
		
		
		val br = new BufferedReader(new FileReader(infile))
		rsOut = new PrintWriter(new FileWriter(outfile + ".rs"))
		connOut = new PrintWriter(new FileWriter(outfile + ".conn.r"))
		rssiOut = new PrintWriter(new FileWriter(outfile + ".rssi.r"))
		connOut.println(List("time", "dst", "src", "rssi", "lqi", "rcv", "loss").mkString(" "))
		rssiOut.println(List("time", "node", "min", "max", "sum", "count").mkString(" "))
		
		
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)
		//                            2013-02-05T18:19:09 
		var ctr = 0;
		for(l <- lIt){
			parseLine(l)
			ctr += 1
			
		}
		
		rsOut.close
		connOut.close
		rssiOut.close
		
		println("Input:        " + ctr)
		println("Output:       " + cout)
		println("Removed dups: " + rem)
		println("Start:        " + dp.format(new Date(startDate)) )
		println("End:          " + dp.format(new Date(endDate)) )
		println
		
		


		
	}

}