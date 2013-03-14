// From http://stackoverflow.com/questions/5805496/arithmetic-expression-grammar-and-parser

package de.fau.realsim.wisebed


import scala.math._
import scala.util.parsing.combinator._
import scala.util.Random
import scala.language.implicitConversions

class FormulaParser(val constants: Map[String,Double] = Map(), /*val userFcts: Map[String,String => Double] = Map(),*/ random: Random = new Random) extends JavaTokenParsers {
  //require(constants.keySet.intersect(userFcts.keySet).isEmpty)
  private val allConstants = constants ++ Map("E" -> E, "PI" -> Pi, "Pi" -> Pi) // shouldn´t be empty
  private val unaryOps: Map[String,Double => Double] = Map(
   "sqrt" -> (sqrt(_)), "abs" -> (abs(_)), "floor" -> (floor(_)), "ceil" -> (ceil(_)), "ln" -> (math.log(_)), "round" -> (round(_)), "signum" -> (signum(_))
  )
  private val binaryOps1: Map[String,(Double,Double) => Double] = Map(
   "+" -> (_+_), "-" -> (_-_), "*" -> (_*_), "/" -> (_/_), "^" -> (pow(_,_))
  )
  private val binaryOps2: Map[String,(Double,Double) => Double] = Map(
   "max" -> (max(_,_)), "min" -> (min(_,_))
  )
  private def fold(d: Double, l: List[~[String,Double]]) = l.foldLeft(d){ case (d1,op~d2) => binaryOps1(op)(d1,d2) } 
  private implicit def map2Parser[V](m: Map[String,V]) = m.keys.map(_ ^^ (identity)).reduceLeft(_ | _)
  private def expression:  Parser[Double] = sign~term~rep(("+"|"-")~term) ^^ { case s~t~l => fold(s * t,l) }
  private def sign:        Parser[Double] = opt("+" | "-") ^^ { case None => 1; case Some("+") => 1; case Some("-") => -1; case w:Any =>  throw new Exception("Something went horribly wrong")}
  private def term:        Parser[Double] = longFactor~rep(("*"|"/")~longFactor) ^^ { case d~l => fold(d,l) }
  private def longFactor:  Parser[Double] = shortFactor~rep("^"~shortFactor) ^^ { case d~l => fold(d,l) }
  private def shortFactor: Parser[Double] = fpn | sign~(constant | rnd | unaryFct | binaryFct | /* userFct |*/ "("~>expression<~")") ^^ { case s~x => s * x }
  private def constant:    Parser[Double] = allConstants ^^ (allConstants(_))
  private def rnd:         Parser[Double] = "rnd"~>"("~>fpn~","~fpn<~")" ^^ { case x~_~y => require(y > x); x + (y-x) * random.nextDouble } | "rnd" ^^ { _ => random.nextDouble }
  private def fpn:         Parser[Double] = floatingPointNumber ^^ (_.toDouble) 
  private def unaryFct:    Parser[Double] = unaryOps~"("~expression~")" ^^ { case op~_~d~_ => unaryOps(op)(d) }
  private def binaryFct:   Parser[Double] = binaryOps2~"("~expression~","~expression~")" ^^ { case op~_~d1~_~d2~_ => binaryOps2(op)(d1,d2) }
  //private def userFct:     Parser[Double] = userFcts~"("~(expression ^^ (_.toString) | ident)<~")" ^^ { case fct~_~x => userFcts(fct)(x) }
  def evaluate(formula: String) = parseAll(expression,formula).get
}