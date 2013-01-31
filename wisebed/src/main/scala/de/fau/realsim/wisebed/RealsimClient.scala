package de.fau.realsim.wisebed

import java.util.Calendar
import java.util.GregorianCalendar
import scala.collection.JavaConversions.asScalaBuffer
import scala.xml.XML
import org.apache.log4j.Level
import de.fau.wisebed.Reservation.reservation2CRD
import de.fau.wisebed.messages.MessageWaiter
import de.uniluebeck.itm.tr.util.Logging
import eu.wisebed.api.common._
import de.fau.wisebed.jobs.MoteAliveState._
import de.fau.wisebed.messages.MessageLogger
import de.fau.wisebed.messages.MsgLiner
import de.fau.wisebed.wrappers._
import de.fau.wisebed.wrappers.WrappedChannelHandlerConfiguration._
import de.fau.wisebed.wrappers.WrappedMessage._
import de.fau.wisebed._
import org.slf4j.LoggerFactory
import de.fau.wisebed.jobs.MoteFlashState
import scala.collection.JavaConversions._
import java.text.SimpleDateFormat
import java.util.Date



object RealsimClient {
	val log = LoggerFactory.getLogger(this.getClass)

	val loops = 100
	val time = 30 * 60

	def main(args: Array[String]) {
		Logging.setLoggingDefaults(Level.DEBUG) // new PatternLayout("%-11d{HH:mm:ss,SSS} %-5p - %m%n"))

		//Get Config

		val conffile = { if (args.length > 1) args(1) else "config.xml" }

		val config = XML.load(conffile)

		val smEndpointURL = (config \ "smEndpointURL").text

		val prefix = (config \ "prefix").text
		val login = (config \ "login").text
		val password = (config \ "password").text

		//Get Settings
		val setfile = { if (args.length > 0) args(0) else "settings.xml" }
		val settings = XML.load(setfile)

		var exp_motes = (settings \ "mote").map(_.text.trim)
		val exp_time = new FormulaParser().evaluate((settings \ "time").text.trim).toInt
		val exp_firmware = (settings \ "firmware").text.trim
		val logname = (settings \ "output").text.trim
		

		//Get Motes
		log.debug("Starting Testbed")
		val tb = new Testbed(smEndpointURL)
		log.debug("Requesting Motes")
		val motesAvail = tb.getnodes()
		log.debug("Motes: " + motesAvail.mkString(", "))

		
		if(exp_motes.size  == 0) exp_motes = motesAvail
		
		if (!exp_motes.forall(motesAvail.contains(_))) {
			log.error("Not all motes available. Have: {}; Need: {} ", motesAvail.mkString(", "), exp_motes.mkString(", "))
			sys.exit(1)
		}
		
		
		log.debug("Logging in: \"" + prefix + "\"/\"" + login + "\":\"" + password + "\"")
		tb.addCredencials(prefix, login, password)

		
		log.debug("Requesting reservations")
		var res = tb.getReservations(exp_time)

		def cleanup(rv: Int) {
			log.debug("Removing Reservation")
			res.foreach(tb.freeReservation(_))
			log.info("Exit with rv: " + rv)
			sys.exit(rv)
		}

		for (r <- res) {
			log.debug("Got Reservations: \n" + r.dateString() + " for " + r.getNodeURNs.mkString(", "))
		}

		if (!res.exists(_.now)) {
			log.debug("No Reservations or in the Past- Requesting")
			val from = new GregorianCalendar
			val to = new GregorianCalendar
			from.add(Calendar.MINUTE, -1)
			to.add(Calendar.MINUTE, exp_time + 8000)
			val r = tb.makeReservation(from, to, exp_motes, "login")
			log.debug("Got Reservations: \n" + r.dateString() + " for " + r.getNodeURNs.mkString(", "))
			res ::= r
		}

		val exp = new Experiment(res.toList, tb)

		//Add general Logger
		exp.addMessageInput(new MessageLogger(mi => {
			import wrappers.WrappedMessage._
			log.info("M(" + mi.node + "): " + mi.dataString)
		}) with MsgLiner)

		log.debug("Requesting Motestate")
		val statusj = exp.areNodesAlive(exp_motes)
		val status = statusj.status
		for ((m, s) <- status) log.info(m + ": " + s)

		val activemotes = (for ((m, s) <- status; if (s == Alive)) yield m).toList

		//Test whether all motes ar available
		if (!exp_motes.forall(activemotes.contains(_))) {
			log.error("Not all motes active. Have: " +  motesAvail.mkString(", ") + "; Need: " +   exp_motes.mkString(", ") + 
					"; Miss: " + exp_motes.filter(!activemotes.contains(_))) 
			cleanup(1)
		}
		
		
		log.debug("Requesting Supported Channel Handlers")
		val handls = exp.supportedChannelHandlers

		val setHand = "contiki"

		if (handls.find(_.name == setHand) == None) {
			log.error("Can not set handler: {}", setHand)
			for (h <- handls) {
				println(h.format)
			}

			cleanup(1)
		} else {
			log.debug("Setting Handler: {}", setHand)
			val chd = exp.setChannelHandler(activemotes, new WrappedChannelHandlerConfiguration("contiki"))
			if (!chd.success) {
				log.error("Failed setting Handler")
				cleanup(1)
			}
		}

		//Go, flash go.

		var motes = activemotes
if(false){
		for (t <- 1 to 5) if (motes.size > 0) {
			log.debug("Flashing  - try " + t)
			val flashj = exp.flash(exp_firmware, motes)
			motes = flashj().filter(_._2 != MoteFlashState.OK).map(_._1).toList

			if (motes.size > 0) {
				log.error("Failed to flash nodes: " + motes.mkString(", "))
			}
		}
		//Are there still motes to flash?
		if (motes.size > 0) {
			cleanup(1)
		}
}
		
		val dt  = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").format(new Date);
		
		//Now, let's log the output. We might loose one or two messages at the beginning, but that's ok (depending on how many times we falshed)
		val out = new java.io.PrintWriter(dt + logname)


		val logger = new MessageLogger(mi => {
			import wrappers.WrappedMessage._
			val df  = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			out.println(df.format(new Date)+ " " + mi.node + ":" + mi.dataString)
			//Flush, so one can terminate an any time
			out.flush
			
		}) with MsgLiner
		
		
		
		
		val rsim = new RealSimFile(dt + "log.rs", 10);

		logger.runOnExit({out.close})
		

		exp.addMessageInput(logger)
		exp.addMessageInput(rsim)
		
		
		
		Runtime.getRuntime.addShutdownHook(new Thread {
			override def run  {
				log.debug("Removing Reservation")
				res.foreach(tb.freeReservation(_))
				log.debug("Waiting 1 sec to clean up.")
				Thread.sleep(1000)
				log.debug("Going down.")
			}
		})
		
		
		while(exp.active){
			Thread.sleep(1000);
			
		}



		sys.exit(0)
		
	}



}
