// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.osgi

import scala.util.parsing.combinator.JavaTokenParsers
import ExportPackages.{Parameter, Export}
import com.yahoo.container.plugin.util.Extractors.ListOf
import scala.util.parsing.input.CharSequenceReader
import scala.annotation.tailrec

/**
 * @author  tonytv
 */
object ExportPackageParser extends JavaTokenParsers {
  val ListOfParameter = new ListOf(classOf[Parameter])


  def exportPackage = rep1sep(export, ",")

  //TODO: remove when fix is in current scala library
  //Fix for https://github.com/scala/scala-parser-combinators/pull/4
  def stringLiteral_fixed: Parser[String] = ("\""+"""([^"\p{Cntrl}\\]|\\[\\'"bfnrt]|\\u[a-fA-F0-9]{4})*+"""+"\"").r

  @SuppressWarnings(Array("unchecked"))
  def export : Parser[Export] = packageName ~ opt(";" ~> (parameters | export)) ^^ {
    case (packageName : String) ~ optional => {
      optional match {
        case None => Export(List(packageName.asInstanceOf[String]), List())
        case Some(e: Export) => e.copy(packageNames = packageName +: e.packageNames)
        case Some(ListOfParameter(parameters)) => Export(List(packageName), parameters)
      }
    }
  }

  def parameters = rep1sep(parameter, ";")

  def parameter = (directive | attribute) ^^ {
    case k ~ v => Parameter(k.toString, v.toString)
  }

  def directive = (extended_ <~ ":=") ~ argument
  def attribute = (extended_ <~ "=") ~ argument

  def packageName = rep1sep(ident_, ".") ^^ {
    x => x.mkString(".")
  }

  def extended = rep1("""\p{Alnum}""".r | "_" | "-" | ".") ^^ {
    _.mkString
  }

  def argument = (extended_ | stringLiteral_ | failure("argument expected")) ^^ {
    val quote = '"'.toString
    _.toString.stripPrefix(quote).stripSuffix(quote)
  }

  def parseAll(in: CharSequence): ParseResult[List[Export]] = {
    try {
      parseAll(exportPackage, in)
    } catch {
      case e: StackOverflowError =>
        throw new RuntimeException("Failed parsing Export-Package: '''\n" + in + "\n'''", e)
    }
  }

  //*** For debugging StackOverflow error **/
  def ident_ = printStackOverflow(ident)("ident")
  def stringLiteral_ = printStackOverflow(stringLiteral_fixed)("stringLiteral_fixed")
  def extended_ = printStackOverflow(extended)("extended")

  def printStackOverflow[T](p: => Parser[T])(name: String): Parser[T] = Parser{ in =>
    try {
      p(in)
    } catch {
      case e: StackOverflowError =>
        val input = in match {
          case reader: CharSequenceReader => readerToString(reader)
          case other => other.toString
        }
        println(s"***StackOverflow for $name with input '''$input'''")
        throw e
    }
  }

  @tailrec
  def readerToString(reader: CharSequenceReader, current: String = ""): String = {
    if (reader.atEnd) current
    else readerToString(reader.rest, current + reader.first)
  }
}
