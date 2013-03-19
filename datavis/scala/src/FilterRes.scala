import java.io.BufferedReader
import java.io.FileReader
import java.text.SimpleDateFormat
import scala.collection.mutable.HashMap
import scala.collection.mutable.ArrayBuffer
import java.io.BufferedWriter
import java.io.FileWriter
import scala.Array.canBuildFrom



class ConLink(var ld: Long,  var del:Boolean, val dat:ArrayBuffer[String])

/**
 * Filter results (Nodes with duplicate output)
 * Add NA, when  there is no connection
 */

object FilterRes {

	val lnks = HashMap[Tuple2[Int, Int], ConLink]()
	
	val dp=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	println("-------")
	var nacnt = 0;
	
	
	def parseLine(l:String) {
		val el = l.split(" ")
		val d = dp.parse(el(0));
		val numb = try{
			 el.tail.tail.map(java.lang.Integer.parseInt(_, 16))
		}
		if(el.length < 7) return
		if(numb(0) == 0 || numb(1) == 0) return
				
		val ds = Tuple2(numb(0), numb(1))
		
		lnks.get(ds) match {
			case Some(r) => 
				val dt = (d.getTime  - r.ld)  / 1000 
				if(dt > 140){
					nacnt += 1
					val lstr = dp.format(r.ld + dt*1000/2) + " " + el(1) + " " + el(2) + " " + el(3) + " NA NA" 
					r.dat += lstr
					r.dat += l
					r.ld = d.getTime
					r.del = false
				} else 	if(r.del == false){
					val cmp = r.dat.last.split(" ");
					if(cmp.length > 4){
						var fail = false;
						for(x <- 2 to 8) {
							if(cmp(x) != el(x)) fail = true;
						}
						if(fail && el(3) != "2c26"){
							
							//println("DUP LINE REM: \n" +r.dat.last + "\n" + l)
							r.dat += l
							r.ld = d.getTime
							r.del = false
						}
					}
					r.del = true;
				} else {
					r.dat += l
					r.ld = d.getTime
					r.del = false
				}
			case None =>
				val ab = ArrayBuffer[String]()
				ab += l
				lnks.put(ds, new ConLink(d.getTime, true, ab))
				
		}
	}
	
	
	def main(args: Array[String]): Unit = {
		println("-------")
		
		val br = new BufferedReader(new FileReader(args(0)))
		println("\nParsing: " + args(0) )
		
		val lIt = Iterator.continually(br.readLine()).takeWhile(_ != null)
		//                            2013-02-05T18:19:09 
		var ctr = 0;
		for(l <- lIt){
			parseLine(l)
			ctr += 1
			
		}
		
		
		val bw = new BufferedWriter(new FileWriter(args(0) + ".par"))
		println("Size: " + lnks.size)
		println("Line: " + ctr)
		println("NACNT: " + nacnt)
		
		lnks.foreach(p => {bw.write(p._2.dat.mkString("\n", "\n", "") )} )
		
		
	}

}