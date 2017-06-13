// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml

import com.yahoo.component.ComponentSpecification
import com.yahoo.search.grouping.GroupingValidator

import scala.language.implicitConversions
import BundleInstantiationSpecificationBuilderTest._
import com.yahoo.config.model.builder.xml.test.DomBuilderTest
import org.hamcrest.CoreMatchers._
import org.junit.Assert._
import org.junit.Test
import org.w3c.dom.Element

import scala.xml.Elem

/**
 * @author gjoranv
 * @since 5.45
 */

class BundleInstantiationSpecificationBuilderTest {

  @Test
  def bundle_is_not_replaced_for_user_defined_class() {
    val userDefinedClass = "my own class that will also be set as bundle"
    verifyExpectedBundle(userDefinedClass,
                         expectedBundle = userDefinedClass)
  }

  @Test
  def bundle_is_replaced_for_internal_class() = {
    val internalClass = classOf[GroupingValidator].getName
    verifyExpectedBundle(internalClass,
                         expectedBundle = BundleMapper.searchAndDocprocBundle)
  }

  @Test
  def bundle_is_not_replaced_for_internal_class_with_explicitly_set_bundle() = {
    val internalClass = classOf[GroupingValidator].getName
    val explicitBundle = "my-own-implementation"
    verifyExpectedBundle(internalClass,
                         explicitBundle = Some(explicitBundle),
                         expectedBundle = explicitBundle)
  }
}

object BundleInstantiationSpecificationBuilderTest {

  def verifyExpectedBundle(className: String,
                           explicitBundle: Option[String] = None,
                           expectedBundle:String) = {
    val xml = <component id="_" class={className} bundle={explicitBundle.orNull} />

    val spec = BundleInstantiationSpecificationBuilder.build(xml, false)
    assertThat(spec.bundle, is(ComponentSpecification.fromString(expectedBundle)))
  }

  implicit def toDomElement(elem: Elem): Element = {
    DomBuilderTest.parse(elem.toString())
  }
}
