// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

/**
 * @author tonytv
 */
trait Converter[T] {
  def convert(s: String): T
}

object Converter {
  def toConverter[T](f: String => T) = new Converter[T] {
    override def convert(s: String) = f(s)
  }

  implicit val intConverter = toConverter(_.toInt)
  implicit val longConverter = toConverter(_.toLong)
  implicit val boolConverter = toConverter(_.toBoolean)
  implicit val stringConverter = toConverter(identity)

  implicit val javaIntegerConverter:Converter[Integer] = toConverter(_.toInt)
  implicit val javaLongConverter:Converter[java.lang.Long] = toConverter(_.toLong)
  implicit val javaBooleanConverter:Converter[java.lang.Boolean] = toConverter(_.toBoolean)


}