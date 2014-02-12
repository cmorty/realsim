package de.fau.realsim.wisebed

import java.util.Calendar
import java.util.GregorianCalendar
import scala.collection.JavaConversions.asScalaBuffer
import scala.xml.XML
import org.apache.log4j.Level
import de.fau.wisebed.Reservation.reservation2CRD
import de.fau.wisebed.messages.MessageWaiter
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
import scala.collection.mutable.Buffer
import de.fau.wisebed.messages.MessageInput

object RealsimClient {
	val log = LoggerFactory.getLogger(this.getClass)

	val loops = 100
	val time = 30 * 60

	def main(args: Array[String]) {

		val handler = new ExHandler();
	    Thread.setDefaultUncaughtExceptionHandler(handler);
	    ;{
			import org.apache.log4j._
			val DEFAULT_PATTERN_LAYOUT = "%-23d{yyyy-MM-dd HH:mm:ss,SSS} | %-30.30t | %-30.30c{1} | %-5p | %m%n"
			val oc = new ConsoleAppender(new PatternLayout(DEFAULT_PATTERN_LAYOUT))
			Logger.getRootLogger.setLevel(org.apache.log4j.Level.DEBUG)
			Logger.getRootLogger.addAppender(oc)
		}

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
		log.info("Time String: " + (settings \ "time").text.trim)
		val exp_time = new FormulaParser().evaluate((settings \ "time").text.trim).toInt

		{
			var tm = exp_time;
			var str = ""
			str = ":%02d".format(tm % 60) + str;
			tm /= 60;
			str = "d %02d".format(tm % 24) + str;
			tm /= 24;
			str = tm.toString + str;

			log.info("TimeCalculated: " + str)
		}

		val exp_firmware = (settings \ "firmware").text.trim
		val outputs = (settings \ "output")
		val rsClient = (settings \ "rsClient")

		val contError = (settings \ "onError").text.trim.toLowerCase.equals("continue")

		// Generate message inputs to make sure everything is ok
		val msgInpts = {
			// Add normal loggers
			val rv = Buffer[MessageInput]()
			val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			for (outp <- outputs) {
				val dt = new SimpleDateFormat(outp.text.trim).format(new Date)
				val out = new java.io.PrintWriter(dt)
				var flush = (new Date).getTime
				val logger = new MessageLogger(mi => {
					import wrappers.WrappedMessage._
					out.println(df.format(new Date) + " " + mi.node + ":" + mi.dataString)
					val now = (new Date).getTime
					if (now - flush > 1000) {
						flush = now
						out.flush
					}
					//

				}) with MsgLiner

				logger.runOnExit({ out.close })
				rv += logger
			}

			for (rsConf <- rsClient) {
				rv += new RealSimLiveConn((rsConf \ "host").text.trim, (rsConf \ "port").text.trim.toInt)
			}

			rv.toSeq
		}

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

		var usemotes = { if (exp_motes.length > 0) exp_motes else motesAvail }

		log.info("Logging in: \"" + prefix + "\"/\"" + login + "\":\"" + password + "\"")
		tb.addCredencials(prefix, login, password)

		log.info("Requesting reservations")
		//Three minutes to flash
		var res = tb.getReservations(exp_time + 3)

		def cleanup(rv: Int) {
			log.info("Removing Reservation")
			res.foreach(tb.freeReservation(_))
			log.info("Exit with rv: " + rv)
			Thread.setDefaultUncaughtExceptionHandler(null)
			sys.exit(rv)
		}

		for (r <- res) {
			log.info("Got Reservation: " + r.dateString("yyyy-MM-dd'T'HH:mm:ss") + " for " + r.getNodeURNs.mkString(", "))
			if (r.now) {
				val to = new GregorianCalendar
				to.add(Calendar.MINUTE, exp_time - 1)
				if (to.after(r.to)) {
					log.error("Experimentation slot to short to run experiment")
					cleanup(3)
				}
			}
		}

		if (!res.exists(_.now)) {
			log.info("No Reservations or in the Past- Requesting")
			val from = new GregorianCalendar
			val to = new GregorianCalendar
			from.add(Calendar.MINUTE, -1)
			to.add(Calendar.MINUTE, exp_time + 3)
			val r = tb.makeReservation(from, to, usemotes, "login")
			log.info("Got Reservations: \n" + r.dateString() + " for " + r.getNodeURNs.mkString(", "))
			res ::= r
		}

		val exp = new Experiment(res.toList, tb)
		/*
		
		Runtime.getRuntime.addShutdownHook(new Thread {
			override def run  {
				log.info("Removing Reservation")
				res.foreach(tb.freeReservation(_))
				log.info("Waiting 1 sec to clean up.")
				Thread.sleep(1000)
				log.info("Going down.")
			}
		})
		
		
*/

		log.info("Requesting Motestate")
		val statusj = exp.areNodesAlive(usemotes)
		val status = statusj.status
		for ((m, s) <- status) log.info(m + ": " + s)

		val activemotes = (for ((m, s) <- status; if (s == Alive)) yield m).toList

		log.info("Active Motes: " + activemotes.mkString(", "))

		if (exp_motes.length > 0) {
			//Test whether all motes are available
			if (!exp_motes.forall(activemotes.contains(_))) {
				log.error("Not all motes active. Have: " + motesAvail.mkString(", ") + "; Need: " + exp_motes.mkString(", ") +
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

		for (t <- 1 to 5) if (motes.size > 0) {
			log.info("Flashing  - try " + t)
			val flashj = {
				if (exp_firmware != "") exp.flash(exp_firmware, motes)
				else exp.flash(getClass.getResourceAsStream("/statprinter.ihex"), motes)
			}
			motes = flashj().filter(_._2 != MoteFlashState.OK).map(_._1).toList

			if (motes.size > 0) {
				log.error("Failed to flash nodes: " + motes.mkString(", "))
				Thread.sleep(10000) //Sleep one sec - just in case
			}
		}
		log.info("Done flashing")

		//Are there still motes to flash?
		if (motes.size > 0) {
			if (contError) {
				log.warn("Continueing despite error")
			} else {
				cleanup(1)
			}
		}

		//Add general Logger
		log.info("Adding generic Logger")

		{
			var ctr = 0
			exp.addMessageInput(new MessageLogger(mi => {
				ctr += 1
				if (ctr == 500) {
					log.info("got 500 Messages")
					ctr = 0;
				}
			}) with MsgLiner)
		}

		//Add other message inputs
		log.info("Adding additional Inputs")
		msgInpts.foreach(exp.addMessageInput(_))

		log.info("Setting Timer")
		val endt = new GregorianCalendar
		endt.add(Calendar.MINUTE, exp_time)
		while (exp.active && endt.after(new GregorianCalendar)) {
			//			log.info(endt.toString() + " -> " + (new GregorianCalendar).toString )
			Thread.sleep(1000)
		}
		log.info("Done");
		val st = Thread.getAllStackTraces

		for (t <- st) {
			if (t._1.isDaemon()) {
				println("Deamon: " + t._1.toString)
			} else {
				println("Thread: " + t._1.toString)
				t._2.foreach(println(_))
			}
		}
		cleanup(0)
	}

}

class ExHandler extends Thread.UncaughtExceptionHandler {
	def uncaughtException(t: Thread, e: Throwable) {
		System.err.println("Throwable: " + e.getMessage());
		System.err.println(t.toString());
		System.err.println("Terminating");
		sys.exit(55)
	}
}

