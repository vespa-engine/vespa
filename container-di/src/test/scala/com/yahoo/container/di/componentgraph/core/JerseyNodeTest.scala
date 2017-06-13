// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

import java.util
import java.util.Collections
import com.yahoo.container.di.osgi.OsgiUtil
import org.junit.Test
import org.junit.Assert._
import org.hamcrest.CoreMatchers.is
import org.hamcrest.Matchers.{contains, containsInAnyOrder}
import org.osgi.framework.wiring.BundleWiring
import scala.collection.JavaConverters._
import com.yahoo.container.bundle.MockBundle

/**
 *
 * @author gjoranv
 * @since 5.17
 */

class JerseyNodeTest {

  trait WithMockBundle {
    object bundle extends MockBundle {
      val entry = Map(
        "com/foo" -> "Foo.class",
        "com/bar" -> "Bar.class)"
      ) map { case (packageName, className) => (packageName, packageName + "/" + className)}


      override def listResources(path: String, ignored: String, options: Int): util.Collection[String] = {
        if ((options & BundleWiring.LISTRESOURCES_RECURSE) != 0 && path == "/") entry.values.asJavaCollection
        else Collections.singleton(entry(path))
      }
    }

    val bundleClasses = bundle.entry.values.toList
  }

  @Test
  def all_bundle_entries_are_returned_when_no_packages_are_given() {
    new WithMockBundle {
      val entries = OsgiUtil.getClassEntriesInBundleClassPath(bundle, Set()).asJavaCollection
      assertThat(entries, containsInAnyOrder(bundleClasses: _*))
    }
  }

  @Test
  def only_bundle_entries_from_the_given_packages_are_returned() {
    new WithMockBundle {
      val entries = OsgiUtil.getClassEntriesInBundleClassPath(bundle, Set("com.foo")).asJavaCollection
      assertThat(entries, contains(bundle.entry("com/foo")))
    }
  }

  @Test
  def bundle_info_is_initialized() {
    new WithMockBundle {
      val bundleInfo = JerseyNode.createBundleInfo(bundle, List())
      assertThat(bundleInfo.symbolicName, is(bundle.getSymbolicName))
      assertThat(bundleInfo.version, is(bundle.getVersion))
      assertThat(bundleInfo.fileLocation, is(bundle.getLocation))
    }
  }

}
