package de.fau.wisebed

import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Timer
import java.util.TimerTask
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.mutable.Buffer
import scala.parallel.Future
import scala.util.Random
import scala.xml.XML
import org.apache.log4j.Level
import Reservation.reservation2CRD
import de.fau.wisebed.jobs.FlashJob
import de.fau.wisebed.jobs.Job
import de.fau.wisebed.jobs.NodeOkFailJob
import de.fau.wisebed.messages.MessageWaiter
import de.uniluebeck.itm.tr.util.Logging
import eu.wisebed.api.common._
import jobs.MoteAliveState._
import messages.MessageLogger
import messages.MsgLiner
import wrappers._
import wrappers.WrappedChannelHandlerConfiguration._
import wrappers.WrappedMessage._
import org.slf4j.LoggerFactory
import de.fau.wisebed.jobs.MoteFlashState
import scala.collection.JavaConversions._


object ExperimentClient {
	val log = LoggerFactory.getLogger(this.getClass)

	val loops = 100
	val time = 30 * 60

	def main(args: Array[String]) {
		Logging.setLoggingDefaults(Level.DEBUG) // new PatternLayout("%-11d{HH:mm:ss,SSS} %-5p - %m%n"))

		//setup

		val setfile = { if (args.length > 1) args(1) else "settings.xml" }

		val settings = XML.load(setfile)

		val smEndpointURL = (settings \ "smEndpointURL").text
		val snaaEndpointURL = (settings \ "snaaEndpointURL").text
		val rsEndpointURL = (settings \ "rsEndpointURL").text

		val prefix = (settings \ "prefix").text
		val login = (settings \ "login").text
		val password = (settings \ "password").text

		//Get Config
		val conffile = { if (args.length > 0) args(0) else "exp.xml" }
		val expconf = XML.load(conffile)

		val exp_motes = (expconf \ "mote").map(_.text.trim)
		val exp_time = (expconf \ "time").text.trim.toInt
		val exp_firmware = (expconf \ "firmware").text.trim

		val exp_runs = (expconf \ "runs")

		//Get Motes
		log.debug("Starting Testbed")
		val tb = new Testbed(smEndpointURL, snaaEndpointURL, rsEndpointURL)
		log.debug("Requesting Motes")
		val motesAvail = tb.getnodes()
		log.debug("Motes: " + motesAvail.mkString(", "))

		log.debug("Logging in: \"" + prefix + "\"/\"" + login + "\":\"" + password + "\"")
		tb.addCredencials(prefix, login, password)

		if (!exp_motes.forall(motesAvail.contains(_))) {
			log.error("Not all motes available. Have: {}; Need: {} ", motesAvail.mkString(", "), exp_motes.mkString(", "))
			sys.exit(1)
		}

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

		exp.addMessageInput(new MessageLogger(mi => {
			import wrappers.WrappedMessage._
			log.info("M(" + mi.node + "): " + mi.dataString)
		}) with MsgLiner)

		log.debug("Requesting Motestate")
		val statusj = exp.areNodesAlive(exp_motes)
		val status = statusj.status
		for ((m, s) <- status) log.info(m + ": " + s)

		val activemotes = (for ((m, s) <- status; if (s == Alive)) yield m).toList

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

		if (args.length > 0) {

			var motes = activemotes
			for (t <- 1 to 5) if (motes.size > 0) {
				log.debug("Flashing  - try " + t)
				val flashj = exp.flash(exp_firmware, motes)
				motes = flashj().filter(_._2 != MoteFlashState.OK).map(_._1).toList

				if (motes.size > 0) {
					log.error("Failed to flash nodes: " + motes.mkString(", "))
				}
			}
			if (motes.size > 0) {
				cleanup(1)
			}
		}

		log.debug("Waiting for nodes to restart.")
		Thread.sleep(5 * 1000)

		var expr = 0
		val lrand = new Random(0);
		for (r <- exp_runs) {
			val num = (r \ "num").text.trim.toInt
			val rnd: Int = {
				val rndv = r \ "rand"
				if (rndv.length > 0) rndv.text.trim.toInt else lrand.nextInt
			}
			val startupt: Int = {
				val stv = r \ "startup"
				if (stv.length > 0) stv.text.trim.toInt else 3
			}
			for (i <- 0 until num) {
				
				if (! runtest(exp, exp_motes, time, "exp" + expr + "_" + i.formatted("%03d") + ".log", startupt, rnd)) {
					cleanup(2)
				}
			}
			expr += 1
		}

		log.debug("Removing Reservation")
		res.foreach(tb.freeReservation(_))
		if(false){
			val st = Thread.getAllStackTraces
			
			for(t <- st){
				if(t._1.isDaemon()){
					println("Deamon: " + t._1.toString)
				} else {
					println("Thread: " +  t._1.toString)
					t._2.foreach(println(_))
				}
			}
		}
		log.debug("Waiting 4 sec to clean up.")
		Thread.sleep(4000)
		log.debug("Going down.")
		sys.exit(0)
		
	}

	def runtest(exp: Experiment, motes: Seq[String], sec: Int, logname: String, startup: Int = 0, rand: Int = 0): Boolean = {
		
		val out = new java.io.PrintWriter(logname)
		var secrem = sec

		val logger = new MessageLogger(mi => {
			import wrappers.WrappedMessage._
			out.println(mi.node + ":" + mi.dataString)
		}) with MsgLiner

		logger.runOnExit({out.close})
		
		val bootupWaiter = new MessageWaiter(motes, "Starting")

		exp.addMessageInput(logger)
		exp.addMessageInput(bootupWaiter)

		
		
		def resetnds:Boolean = {
			log.debug("Resetting nodes")
			var resj: List[Job[_]] = null
			if (startup == 0) {
				val resj = List(exp.resetNodes(motes))
	
			} else {
				val tm = new Date
				val rnd = new Random(rand)
				val ftrs = Buffer[Future[Job[_]]]()
	
				for (mote <- motes) {
					val restm = new Date(tm.getTime + 1000 + (rnd.nextInt % (startup * 1000)))
					val ftr = new TimerTask with Future[NodeOkFailJob] {
						var j: NodeOkFailJob = null;
						def isDone = (j != null)
						def apply: NodeOkFailJob = synchronized { while (!isDone) wait; j }
						def run = synchronized {
							j = exp.resetNodes(List(mote))
							notify
						}
					}
					ftrs += ftr
					val tmr = new Timer(true)
					tmr.schedule(ftr, restm)
				}
				//Get jobs from futures
				resj = ftrs.map(_()).toList
	
			}
			if (!resj.forall(_.success)) {
				log.error("Failed to reset nodes")
				return false
			}
	
			log.debug("Waiting for bootup")
			if (!bootupWaiter.waitTimeout(60 * 1000)) {
				bootupWaiter.unregister
				log.error("Failed to Boot")
				return false
			}
			true
		}
		
		
		try{
		
			var reset = false
			for(t <-  1 to 5)if(!reset){
				reset = resetnds;
				if(t == 5 && ! reset){
					return false
				}
			}
				
			
			
			
			log.debug("Starting")
			val snd = exp.send(motes(0), "go\n")
			if (!snd.success) {
				log.error("Failed to send information to nodes")
				return false
			}
			log.debug("Running")
			while (secrem > 60) {
				log.info("Time left: ~" + (((secrem - 1) / 60) + 1) + "min");
				Thread.sleep(60 * 1000)
				secrem -= 60
			}
			Thread.sleep(secrem * 1000)
	
			//Collect stats
			val statWaiter = new MessageWaiter(motes, "===")
			exp.addMessageInput(statWaiter)
	
			val snds = exp.send(motes, "stats\n")
			if (!snds.success) {
				log.error("Failed to send information to nodes")
				return false
			}
	
			log.debug("Waiting for stats")
			if (!statWaiter.waitTimeout(10 * 1000)) {
				log.error("Failed to collect stats")
				return false
			}
			exp.remMessageInput(logger)
			log.debug("DONE")
			
			true
		} catch {
			case e => {
				log.error("Something failed: ", e)
				false
			}
		} 
		

		
	}
}
