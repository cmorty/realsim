package de.fau.realsim.wisebed

import java.util.Calendar
import java.util.GregorianCalendar

import scala.xml.XML

import org.slf4j.LoggerFactory

import de.fau.wisebed.Experiment
import de.fau.wisebed.Testbed
import de.fau.wisebed.WisebedApiConversions.message2wmessage
import de.fau.wisebed.jobs.NodeAliveState.Alive
import de.fau.wisebed.jobs.NodeFlashState
import de.fau.wisebed.messages.MessageInput
import de.fau.wisebed.messages.MessageLogger
import de.fau.wisebed.messages.MsgLiner
import de.fau.wisebed.messages.NodeFilter
import de.fau.wisebed.util.Logging.setDefaultLogger
import de.fau.wisebed.wrappers.ChannelHandlerConfiguration



object SetNodeID {

	val log = LoggerFactory.getLogger(this.getClass)

	val loops = 100
	val time = 30 * 60
	
	
	def main(args: Array[String]) {
		setDefaultLogger

		//Get Config

		val conffile = { if (args.length > 1) args(1) else "config.xml" }

		log.info("Loading Wisebed config: " + conffile)
		
		val config = XML.load(conffile)

		val smEndpointURL = (config \ "smEndpointURL").text
	
		val prefix = (config \ "prefix").text
		val login = (config \ "login").text
		val password = (config \ "pass").text

		//Get Settings
		val setfile = { if (args.length > 0) args(0) else "settings.xml" }
		log.info("Loading experiment config: " + setfile)
		val settings = XML.load(setfile)

		val exp_motes = (settings \ "mote").map(_.text.trim)

		


		
		
		//Get Motes
		log.info("Starting Testbed")
		val tb = new Testbed(smEndpointURL)
		log.info("Requesting Motes")
		val motesAvail = tb.getNodes("telosb")
		log.info("Motes: " + motesAvail.mkString(", "))

		
		
		if (!exp_motes.forall(motesAvail.contains(_))) {
			log.error("Not all motes available. Have: " + motesAvail.mkString(", ") + "; Need: "  + exp_motes.mkString(", ") + ";");
			sys.exit(1)
		}
		
		var usemotes = {if(exp_motes.length > 0) exp_motes else motesAvail}
		
		log.info("Logging in: \"" + prefix + "\"/\"" + login + "\":\"" + password + "\"")
		tb.addCredencials(prefix, login, password)

		
		log.info("Requesting reservations")
		//Three minutes to flash
		var res = tb.getReservations(3) 

		def cleanup(rv: Int) {
			log.info("Removing Reservation")
			res.foreach(tb.freeReservation(_))
			log.info("Exit with rv: " + rv)
			sys.exit(rv)
		}

		for (r <- res) {
			log.info("Got Reservations: \n" + r.dateString() + " for " + r.getNodeURNs.mkString(", "))
		}
		
		
		if (!res.exists(_.now)) {
			log.info("No Reservations or in the Past- Requesting")
			val from = new GregorianCalendar
			val to = new GregorianCalendar
			from.add(Calendar.MINUTE, -1)
			to.add(Calendar.MINUTE,  3)
			val r = tb.makeReservation(from, to, usemotes, "login")
			log.info("Got Reservations: \n" + r.dateString() + " for " + r.getNodeURNs.mkString(", "))
			res ::= r
		}

		val exp = new Experiment(res.toList, tb)

		
		Runtime.getRuntime.addShutdownHook(new Thread {
			override def run  {
				log.info("Removing Reservation")
				res.foreach(tb.freeReservation(_))
				log.info("Waiting 1 sec to clean up.")
				Thread.sleep(1000)
				log.info("Going down.")
			}
		})
		
		


		log.info("Requesting Motestate")
		val statusj = exp.areNodesAlive(usemotes)
		val status = statusj.status
		for ((m, s) <- status) log.info(m + ": " + s)

		val activemotes = (for ((m, s) <- status; if (s == Alive)) yield m).toList

		log.info("Active Motes: " +  activemotes.mkString(", "))
		
		
		if(exp_motes.length > 0) {
			//Test whether all motes are available
			if (!exp_motes.forall(activemotes.contains(_))) {
				log.error("Not all motes active. Have: " +  motesAvail.mkString(", ") + "; Need: " +   exp_motes.mkString(", ") + 
						"; Miss: " + exp_motes.filter(!activemotes.contains(_))) 
				cleanup(1)
			}
		} else {
			usemotes = activemotes
		}
		
		log.info("Requesting Supported Channel Handlers")
		val handls = exp.supportedChannelHandlers

		val setHand = "contiki"

		if (handls.find(_.name == setHand) == None) {
			log.error("Can not set handler: {}", setHand)
			for (h <- handls) {
				println(h.format)
			}

			cleanup(1)
		} else {
			log.info("Setting Handler: {}", setHand)
			val chd = exp.setChannelHandler(usemotes, new ChannelHandlerConfiguration("contiki"))
			if (!chd.success) {
				log.error("Failed setting Handler")
				cleanup(1)
			}
		}

	

		
		//Go, flash go.
		var motes = usemotes
		
				//Add general Logger
		
		for(mote <- motes){
			var ctr = 0
			def mid:String = "%d".format(java.lang.Integer.decode(mote.split(":").last)) + "\n"
			var unreg:MessageInput = null
			val ml:MessageLogger  with NodeFilter = new MessageLogger(
				mi => {	
					
					if(mi.dataString.startsWith("01:")){
						log.info("Node: " + mote + ": set " + mid)
						exp.send(mote, mid)
					} else if(mi.dataString.startsWith("02:")){
						exp.send(mote, mid)
						log.info("Node: " + mote + ": confirm " + mid)
					} else if(mi.dataString.startsWith("03:")){
						log.info("Node: " + mote + ": succes setting to " + mid)
						exp.remMessageInput(unreg)						
					} else {
						log.warn("Node: " + mote + ": " + mi.dataString)
					}
				}
			) with MsgLiner with NodeFilter
			unreg = ml;
			ml.setNodeFilter(Set(mote))
			exp.addMessageInput(ml)
		}
		
		
		

		for (t <- 1 to 5) if (motes.size > 0) {
			log.info("Flashing  - try " + t)
			val flashj =  exp.flash(getClass.getResourceAsStream("/burn-nodeid.ihex"), motes)
			
			motes = flashj().filter(_._2 != NodeFlashState.OK).map(_._1).toList

			if (motes.size > 0) {
				log.error("Failed to flash nodes: " + motes.mkString(", "))
				Thread.sleep(10000) //Sleep one sec - just in case
			}
		}
		//Are there still motes to flash?
		if (motes.size > 0) {
			cleanup(1)
		}
		
	}
}