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
import gnu.io.CommPort
import gnu.io.CommPortIdentifier
import gnu.io.SerialPort
import org.slf4j.LoggerFactory
import org.apache.log4j.Logger
import org.apache.log4j.ConsoleAppender
import org.apache.log4j.PatternLayout
import java.net.Socket
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.BufferedWriter
import java.io.IOException


object RealSimClient {
	val log = LoggerFactory.getLogger(this.getClass)
	val dp=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
	var dstnode = -1

	val DEFAULT_PATTERN_LAYOUT = "%-23d{yyyy-MM-dd HH:mm:ss,SSS} | %-30.30t | %-30.30c{1} | %-5p | %m%n"
	Logger.getRootLogger.addAppender(new ConsoleAppender(new PatternLayout(DEFAULT_PATTERN_LAYOUT)))
	
	
	def idToString(id:Int) = ("%d.%d").format( id%0x100, id / 0x100 )
	
	
	
	def parseLine(l:String, out:PrintWriter) {
		
		
		
		val el = l.split(" ") 
		var src = 0
		var rssi =0
		var lqi =0 
		var rcv = 0
		var loss = 0
		var numb:Array[Int] = null
		var d:Date = null

		
		if(l.equals("===")) {
			dstnode = -1
			return
		}
		
		if(el.length < 2 ) return
		
		
		def isT(suf:String*):Boolean = {
			suf.exists(el(0).endsWith(_))
		}
		
		if(isT("R:", "DAT:")){
			
			numb = try{
				 el.tail.map(java.lang.Integer.parseInt(_))
			} catch {
				case e:Throwable => 
					println("Failed to pase " + l )
					return
			}
		} else {
			log.trace("Ignoring: " + l)
			return;
		} 
		
		
		if(isT("DAT:")) {
			dstnode = numb(1)
			
		} else if(isT("R:")){
			if(dstnode == -1) {
				log.error("Unexpected R - No node: " + l )
				return
			}
			if(el.length < 6) return
			src = numb(0)
			rssi = numb(1) - 45
			lqi = numb(2);
			rcv = numb(3);
			loss = numb(4);
			// 5: Dups

			//Default time ~60 sec
			out.println( List(240, src, dstnode, (rcv.toFloat * 100/ (rcv+loss)).toInt , rssi, lqi).mkString(" "))
			log.debug("Sending " + List(240, src, dstnode, (rcv.toFloat * 100/ (rcv+loss)).toInt , rssi, lqi).mkString(" "))
			
		}
		
	}
	
	
	def main(args: Array[String]): Unit = {
		var serialdev = ""
		var host = "localhost"
		var port = 1337
		
		val parser = new OptionParser("scopt") {
			arg("<device>", "<device> serial device", { v: String => serialdev = v })
			arg("<host>", "<host> Hostname; Default: " + host, { v: String => host = v })
			arg("<port>", "<port> serial device; Defualt: " + port, { v: String => port = v.toInt })
		
		}
		
		if(!parser.parse(args)) sys.exit(1)
		
		
		log.info("Opening Serial: " + serialdev )
		
		val portIdentifier = CommPortIdentifier.getPortIdentifier(serialdev);
		if ( portIdentifier.isCurrentlyOwned() )
		{
			log.error("Error: Port" + serialdev  +" is currently in use");
			sys.exit(1)
		}
	
		
		val commPort = portIdentifier.open(this.getClass().getName(),2000);
		if(!commPort.isInstanceOf[SerialPort]) {
			log.error(serialdev + " is no serial port.")
			sys.exit(3)
		}
		
		log.info("Connecting to Server")
		
		val sock = new Socket(host, port);
		val sout = new PrintWriter(sock.getOutputStream(), true);
		
		log.info("Setting up serial" )
		val serialPort = commPort.asInstanceOf[SerialPort]
		serialPort.setSerialPortParams(115200,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
		
		
		
		
		val in = new BufferedReader(new InputStreamReader(serialPort.getInputStream))
		val out = new BufferedWriter(new OutputStreamWriter(serialPort.getOutputStream))
		
		try {
			while(!sout.checkError) {
				val ln = in.readLine
				println("L: " + ln)
				parseLine(ln, sout)
			}
			sout.close
			
		} catch {
			case e:IOException => log.info("Serial closed")
			case e:Exception => log.error("Error: " , e)
		}
		
	
		


		
	}

}