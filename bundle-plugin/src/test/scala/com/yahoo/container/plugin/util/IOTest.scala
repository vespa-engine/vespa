// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.plugin.util

import org.scalatest.junit.{JUnitSuite, ShouldMatchersForJUnit}
import org.junit.Test

import IO.using
import java.io.Closeable

/**
 * @author  tonytv
 */
class IOTest extends JUnitSuite with ShouldMatchersForJUnit  {
  class ClosingException extends RuntimeException
  class FunctionException extends RuntimeException

  object throwWhenClosingResource extends Closeable {
    def close() {
      throw new ClosingException();
    }
  }

  def throwFunction(r : throwWhenClosingResource.type) = throw new FunctionException
  def nonThrowingFunction(r : throwWhenClosingResource.type) = 42

  @Test
  def require_that_function_exception_is_prioritized_over_closing_exception() {
    intercept[FunctionException]{
      using(throwWhenClosingResource, readOnly = false)(throwFunction)
    }
  }

  @Test
  def require_that_closing_exception_is_ignored_when_read_only() {
    using(throwWhenClosingResource, readOnly = true)(nonThrowingFunction) should be (nonThrowingFunction(null))
  }

  @Test
  def require_that_closing_exception_is_rethrown_when_not_read_only() {
    intercept[ClosingException] {
      using(throwWhenClosingResource, readOnly = false)(nonThrowingFunction)
    }
  }
}
