
import scala.io.Source

/**
 * Filter out unneded output.
 */

object RPrep {
	
	
	def main(args: Array[String]): Unit = {
		
		val fin = args(0)
		val fout = args(1)
		
		val out = new java.io.PrintWriter(fout)
		val fda = Source.fromFile(fin).getLines
		for(line <- fda){
			val parts  = line.split(" ")
			if(parts(1).takeRight(2) == "R:" && parts.length  == 9)
				out.println(line)						
		}			
		
	}

}