// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.scalalib.java.function

import java.util.function.Predicate

import scala.language.implicitConversions

/**
 * For using scala functions in Java APIs, such as the stream API.
 * @author tonytv
 */
object FunctionConverters {
  implicit class JavaPredicate[T](f: T => Boolean) extends Predicate[T] {
    override def test(t: T): Boolean = f(t)
  }

  implicit class JavaFunction[T, R](f: T => R) extends java.util.function.Function[T, R] {
    override def apply(t: T): R = f(t)
  }
}
