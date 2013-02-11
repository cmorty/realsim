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
		Logging.setLoggingDefaults(Level.INFO) // new PatternLayout("%-11d{HH:mm:ss,SSS} %-5p - %m%n"))

		//Get Config

		val conffile = { if (args.length > 1) args(1) else "config.xml" }

		val config = XML.load(conffile)

		val smEndpointURL = (config \ "smEndpointURL").text

		val prefix = (config \ "prefix").text
		val login = (config \ "login").text
		val password = (config \ "pass").text

		//Get Settings
		val setfile = { if (args.length > 0) args(0) else "settings.xml" }
		val settings = XML.load(setfile)

		val exp_motes = (settings \ "mote").map(_.text.trim)
		val exp_time = new FormulaParser().evaluate((settings \ "time").text.trim).toInt
		val exp_firmware = (settings \ "firmware").text.trim
		val logname = (settings \ "output").text.trim
		val rsClient = (settings \ "rsClient")
		

		//Get Motes
		log.info("Starting Testbed")
		val tb = new Testbed(smEndpointURL)
		log.info("Requesting Motes")
		val motesAvail = tb.getNodes("telosb")
		log.info("Motes: " + motesAvail.mkString(", "))

		
		
		if (!exp_motes.forall(motesAvail.contains(_))) {
			log.error("Not all motes available. Have: {}; Need: {} ", motesAvail.mkString(", "), exp_motes.mkString(", "))
			sys.exit(1)
		}
		
		var usemotes = {if(exp_motes.length > 0) exp_motes else motesAvail}
		
		log.info("Logging in: \"" + prefix + "\"/\"" + login + "\":\"" + password + "\"")
		tb.addCredencials(prefix, login, password)

		
		log.info("Requesting reservations")
		var res = tb.getReservations(exp_time)

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
			to.add(Calendar.MINUTE, exp_time)
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
			val chd = exp.setChannelHandler(usemotes, new WrappedChannelHandlerConfiguration("contiki"))
			if (!chd.success) {
				log.error("Failed setting Handler")
				cleanup(1)
			}
		}

	

		
		//Go, flash go.
		var motes = usemotes
if(true){
		for (t <- 1 to 5) if (motes.size > 0) {
			log.info("Flashing  - try " + t)
			val flashj = exp.flash(exp_firmware, motes)
			motes = flashj().filter(_._2 != MoteFlashState.OK).map(_._1).toList

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
		
		//Add general Logger
		
		{
			var ctr = 0
			exp.addMessageInput(new MessageLogger(mi => {
				ctr += 1 
				if(ctr == 500) {
					log.info("got 500 Messages")
					ctr = 0;
				}
			}) with MsgLiner)
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
		
		
		
		for(rsConf <- rsClient){
			val rsc = new RealSimLiveConn((rsConf \ "host").text.trim, (rsConf \ "port").text.trim.toInt)
			exp.addMessageInput(rsc)
		}
		
		
		while(exp.active){
			Thread.sleep(1000);
			
		}

		
	}



}
