// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain

import java.util.Optional

import scala.language.implicitConversions
import org.junit.Test
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSuite
import com.yahoo.search.searchchain.model.federation.{FederationOptions, FederationSearcherModel}
import com.yahoo.component.{ComponentSpecification, ComponentId}
import com.yahoo.component.chain.dependencies.Dependencies
import java.util.Collections.{emptyList, emptySet}
import com.yahoo.component.provider.ComponentRegistry

import FederationSearcherTest._
import com.yahoo.component.chain.model.ChainSpecification
import com.yahoo.search.federation.FederationConfig
import com.yahoo.config.ConfigInstance
import com.yahoo.vespa.model.ConfigProducer
import scala.reflect.ClassTag
import scala.collection.JavaConversions._
import scala.collection.breakOut
import com.yahoo.vespa.model.container.search.searchchain.Source.GroupOption
import com.yahoo.search.federation.sourceref.Target
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel.TargetSpec


/**
 * @author tonytv
 */
@RunWith(classOf[JUnitRunner])
class FederationSearcherTest extends FunSuite{

  class FederationFixture {
    val federationSearchWithDefaultSources = newFederationSearcher(inheritDefaultSources = true)
    val searchChainRegistry = new ComponentRegistry[SearchChain]
    val sourceGroupRegistry = new SourceGroupRegistry

    def initializeFederationSearcher(searcher: FederationSearcher = federationSearchWithDefaultSources) {
      searcher.initialize(searchChainRegistry, sourceGroupRegistry)
    }

    def registerProviderWithSources(provider: Provider) = {
      provider :: provider.getSources.toList foreach { chain => searchChainRegistry.register(chain.getId, chain) }
      sourceGroupRegistry.addSources(provider)
    }
  }

  class ProvidersWithSourceFixture extends FederationFixture {
    val provider1 = createProvider("provider1")
    val provider2 = createProvider("provider2")

    provider1.addSource(createSource("source", GroupOption.leader))
    provider2.addSource(createSource("source", GroupOption.participant))

    registerProviderWithSources(provider1)
    registerProviderWithSources(provider2)
    initializeFederationSearcher()
  }

  test("default providers are inherited when inheritDefaultSources=true") {
    val f = new FederationFixture
    import f._

    val providerId = "providerId"

    registerProviderWithSources(createProvider(providerId))
    initializeFederationSearcher()

    val federationConfig = getConfig[FederationConfig](federationSearchWithDefaultSources)
    val target = federationConfig.target(0)

    assert( providerId === target.id() )
    assert( target.searchChain(0).useByDefault(), "Not used by default" )
  }

  def toMapByKey[KEY, VALUE](collection: java.util.Collection[VALUE])(f: VALUE => KEY): Map[KEY, VALUE] =
    collection.map(e => (f(e), e))(breakOut)

  test("source groups are inherited when inheritDefaultSources=true") {
    val f = new ProvidersWithSourceFixture
    import f._

    val federationConfig = getConfig[FederationConfig](federationSearchWithDefaultSources)
    assert(federationConfig.target().size == 1)

    val target = federationConfig.target(0)
    assert(target.id() == "source")
    assert(target.useByDefault(), "Not used by default")

    //val chainsByProviderId = toMapByKey(target.searchChain())(_.providerId())

    assert(Set("provider1", "provider2") === target.searchChain().map(_.providerId()).toSet)
  }

  test("source groups are not inherited when inheritDefaultSources=false") {
    val f = new ProvidersWithSourceFixture
    import f._

    val federationSearcherWithoutDefaultSources = newFederationSearcher(inheritDefaultSources = false)
    initializeFederationSearcher(federationSearcherWithoutDefaultSources)

    val federationConfig = getConfig[FederationConfig](federationSearcherWithoutDefaultSources)
    assert(federationConfig.target().size == 0)
  }

  test("leaders must be the first search chain in a target") {
    val f = new ProvidersWithSourceFixture
    import f._

    val federationConfig = getConfig[FederationConfig](federationSearchWithDefaultSources)
    val searchChain = federationConfig.target(0).searchChain

    assert(searchChain.get(0).providerId() === "provider1")
    assert(searchChain.get(1).providerId() === "provider2")

  }

  test("manually specified targets overrides inherited targets") {
    val f = new FederationFixture
    import f._

    registerProviderWithSources(createProvider("provider1"))
    val federation = newFederationSearcher(inheritDefaultSources = true,
      targets = List(new TargetSpec("provider1", new FederationOptions().setTimeoutInMilliseconds(12345))))

    initializeFederationSearcher(federation)

    val federationConfig = getConfig[FederationConfig](federation)

    assert(federationConfig.target().size === 1)
    val target = federationConfig.target(0)

    assert(target.searchChain().size === 1)
    val searchChain = target.searchChain(0)

    assert(searchChain.timeoutMillis() === 12345)
  }


  def newFederationSearcher(inheritDefaultSources: Boolean,
                             targets: java.util.List[TargetSpec] = emptyList()): FederationSearcher = {
    new FederationSearcher(
      new FederationSearcherModel("federation",
        Dependencies.emptyDependencies(),
        targets,
        inheritDefaultSources),
      Optional.empty())
  }
}

object FederationSearcherTest {
  implicit def toComponentId(name: String): ComponentId = ComponentId.fromString(name)
  implicit def toComponentSpecification(name: String): ComponentSpecification = ComponentSpecification.fromString(name)

  def newBuilder[T <: ConfigInstance.Builder](implicit c: ClassTag[T]): T = {
    c.runtimeClass.newInstance().asInstanceOf[T]
  }

  def searchChainSpecification(id: ComponentId) =
    new ChainSpecification(id, new ChainSpecification.Inheritance(null, null), emptyList(), emptySet())

  def createProvider(id: ComponentId) =
    new Provider(searchChainSpecification(id), new FederationOptions())

  def createSource(id: ComponentId, groupOption: GroupOption) =
    new Source(searchChainSpecification(id), new FederationOptions(), groupOption)


  //TODO: TVT: move
  def getConfig[T <: ConfigInstance : ClassTag](configProducer: ConfigProducer): T = {
    val configClass = implicitly[ClassTag[T]].runtimeClass
    val builderClass = configClass.getDeclaredClasses.collectFirst {case c if c.getSimpleName == "Builder" => c } getOrElse {
      sys.error("No Builder class in ConfigInstance.")
    }

    val builder = builderClass.newInstance().asInstanceOf[AnyRef]
    val getConfigMethod = configProducer.getClass.getMethod("getConfig", builderClass)

    getConfigMethod.invoke(configProducer, builder)

    configClass.getConstructor(builderClass).newInstance(builder).asInstanceOf[T]
  }
}
