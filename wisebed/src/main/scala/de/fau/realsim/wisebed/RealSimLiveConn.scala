package de.fau.realsim.wisebed

import java.net.Socket
import java.net.InetAddress
import de.fau.wisebed.messages.MessageInput
import eu.wisebed.api._
import java.io.PrintStream
import java.util.Date
import de.fau.wisebed.messages.MsgLiner
import de.fau.wisebed.wrappers.WrappedMessage._
import org.slf4j.LoggerFactory

class RealSimLiveConn(host:String = "localhost", port:Int = 1337, timeout:Int = 120) extends MessageInput {
	
	val log = LoggerFactory.getLogger(this.getClass)
	var socket:Socket = null
	var out:PrintStream = null
	var lasttry:Long = 0;
	
	class MessageHandler extends MessageInput {
		val log = LoggerFactory.getLogger(this.getClass)
		def handleMsg(mi:common.Message):Unit = {
			val log = LoggerFactory.getLogger(this.getClass)
			//Socket stuff
			if((lasttry + 5000 < (new Date).getTime) && (socket == null || !socket.isConnected() || out.checkError()) ){
				lasttry = (new Date).getTime
				if(socket != null) socket.close;
				try{
					log.info("Opening Socket: " + host + ":" + port)
					val addr = InetAddress.getByName(host);
					
					socket = new Socket(addr, port)
					out = new PrintStream(socket.getOutputStream)
					
				} catch {
					case e:Throwable => 
						if(e.toString() == "java.net.ConnectException: Connection refused"){
								log.info("Failed to connect. Waiting 5 sec.")
						} else {
							log.error("Failed to open socket " , e)
						}
						if(socket != null) socket.close;						
						socket = null;						 
				}
			}
			
			if(socket != null && socket.isConnected() && !out.checkError()){
				
				val msg =  mi.dataString.split(" ")
					
				if(msg(0) == "R:"){					 
					try{
						 val dat = 0 :: msg.tail.map(java.lang.Integer.parseInt(_, 16)).toList
						 val h = 100f
						 
						 val ratio = "%x".format( (h * dat(5) / (dat(5) + dat(6))).toInt)
						 log.info(msg.mkString("--" ,  "-", "--") +   dat(5) + " " + dat(6) + " = " + ratio)
						 var oele = Array[String]("%x".format(timeout), msg(2), msg(1), ratio ,msg(3), msg(4))
						 log.info("Sending: " +  oele.mkString(" "))
						 out.println(oele.mkString(" "))
					} catch {
						case e:Exception => log.error("Error parsing string: ", e)
					}
					
				}
			} 			
		}
	}
	
	val mh = new MessageHandler with MsgLiner	
		
	def handleMsg(mi:common.Message):Unit = mh.handleMsg(mi)
	
	override def exit(): Nothing  = {
		if(socket != null) socket.close
		super.exit
	}
	
}