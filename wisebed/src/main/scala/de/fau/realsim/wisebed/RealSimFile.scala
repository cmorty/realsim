package de.fau.realsim.wisebed

import de.fau.wisebed.messages.MessageInput
import de.fau.wisebed.messages.MsgLiner
import eu.wisebed.api.common.Message
import org.slf4j.LoggerFactory
import de.fau.wisebed.messages.MessageLogger
import de.fau.wisebed.wrappers.WrappedMessage._
import scala.collection.mutable.Buffer
import scala.collection.mutable.Queue
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import java.util.Calendar
import java.util.GregorianCalendar
import scala.collection.mutable.SynchronizedSet
import scala.collection.mutable.HashSet

private class Average(size:Int) {
	private val q = new Queue[Int]
	def add(e:Int){
		if(q.size == size){
			q.dequeue
		}
		q += e
	}
	def avg:Float = q.sum / q.size
	
	
	def sum = q.sum
} 

private class MoteData(size:Int){
	val rssi = new Average(size)
	val lqi = new Average(size)
	val rcv = new Average(size)
	val loss = new Average(size)
	def add(_rssi:Int, _lqi:Int, _rcv:Int, _loss:Int){
		rssi.add(_rssi)
		lqi.add(_lqi)
		rcv.add(_rcv)
		loss.add(_loss)
	}
}


class RealSimFile(file:String, avgsize:Int) extends MessageInput {
	val log = LoggerFactory.getLogger(this.getClass)
	val out = new java.io.PrintWriter(file)
	private val motes = Map[Tuple2[Int, Int], MoteData]()
	
	
	def idToString(id:Int) = ("%d.%d").format( id%0x100, id / 0x100 )
	
	val addnode =  new HashSet[Int] with SynchronizedSet[Int]
	
	var d:Long = 0;
	
	
	val wo = new MessageLogger ( mi => {
			
		
				val msg =  mi.dataString.split(" ")
				
				//log.info("got msg: " + msg.mkString("\"", "\", \"", "\""))
				if(msg(0) == "R:"){
					if(d == 0) d =  System.currentTimeMillis(); 
					
					val numb = try{
						 msg.tail.map(java.lang.Integer.parseInt(_, 16))
					}
					val dst = numb(0)
					val src = numb(1)
						
					
					val md = motes.getOrElseUpdate(new Tuple2(src, dst),{
						if(addnode.add(src)){ out.println("0;addnode;" + idToString(src)) ; log.debug(addnode.toString + " - " + idToString(src))} 
						if(addnode.add(dst)){ out.println("0;addnode;" + idToString(dst)) ; log.debug(addnode.toString + " - " + idToString(dst))}
						new MoteData(avgsize)
					})

					md.add(numb(2), numb(3), numb(4), numb(5))
					
					val dt = ((System.currentTimeMillis() -d) / 1000 ).toInt; 
					val rat = md.rcv.sum.toFloat / (md.rcv.sum + md.loss.sum)
					log.debug(idToString(src) + " -> " + idToString(dst)  + ": " + md.rcv.sum.toString + " ; " + md.loss.sum + "->"  + "%f".format(rat))
					out.println("%d;setedge;%s;%s;%f;%d;%d".format(dt, idToString(src), idToString(dst), rat, md.rssi.avg.toInt, md.lqi.avg.toInt))
					out.flush
				}
		
			}		
		) with MsgLiner
	
	def handleMsg(msg: Message){
		wo.handleMsg(msg)
	}
}