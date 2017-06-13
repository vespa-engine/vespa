// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.http.filter

import com.yahoo.container.core.ChainsConfig
import com.yahoo.component.provider.ComponentRegistry
import com.yahoo.jdisc.http.filter.chain.{RequestFilterChain, ResponseFilterChain}
import com.yahoo.jdisc.http.filter.{RequestFilter, ResponseFilter}
import com.yahoo.jdisc.http.filter.{SecurityResponseFilterChain, SecurityRequestFilterChain, SecurityResponseFilter, SecurityRequestFilter}
import com.yahoo.component.{ComponentSpecification, ComponentId, AbstractComponent}
import com.yahoo.component.chain.model.ChainsModelBuilder
import com.yahoo.component.chain.{Chain, ChainedComponent, ChainsConfigurer}
import com.yahoo.processing.execution.chain.ChainRegistry
import FilterChainRepository._
import scala.collection.JavaConversions._


/**
 * Creates JDisc request/response filter chains.
 * @author tonytv
 */
class FilterChainRepository(chainsConfig: ChainsConfig,
                            requestFilters: ComponentRegistry[RequestFilter],
                            responseFilters: ComponentRegistry[ResponseFilter],
                            securityRequestFilters: ComponentRegistry[SecurityRequestFilter],
                            securityResponseFilters: ComponentRegistry[SecurityResponseFilter]) extends AbstractComponent {

  private val filtersAndChains = new ComponentRegistry[AnyRef]
  addAllFilters(filtersAndChains, requestFilters, responseFilters, securityRequestFilters, securityResponseFilters)
  addAllChains(filtersAndChains, chainsConfig, requestFilters, responseFilters, securityRequestFilters, securityResponseFilters)
  filtersAndChains.freeze()

  def getFilter(componentSpecification: ComponentSpecification) =
    filtersAndChains.getComponent(componentSpecification)
}

 private[filter] object FilterChainRepository {
  case class FilterWrapper(id: ComponentId, filter: AnyRef) extends ChainedComponent(id) {
    def filterType: Class[_] = filter match {
      case f: RequestFilter  => classOf[RequestFilter]
      case f: ResponseFilter => classOf[ResponseFilter]
      case f: SecurityRequestFilter  => classOf[SecurityRequestFilter]
      case f: SecurityResponseFilter => classOf[SecurityResponseFilter]
      case _ => throw new IllegalArgumentException("Unsupported filter type: " + filter.getClass.getName)
    }
  }

  def allFiltersWrapped(registries: ComponentRegistry[_ <: AnyRef]*): ComponentRegistry[FilterWrapper] = {
    val wrappedFilters = new ComponentRegistry[FilterWrapper]

    def registerWrappedFilters(registry: ComponentRegistry[_ <: AnyRef]) {
      for ((id, filter) <- registry.allComponentsById())
        wrappedFilters.register(id, new FilterWrapper(id, filter))
    }

    registries.foreach(registerWrappedFilters)
    wrappedFilters.freeze()
    wrappedFilters
  }

  private def addAllFilters(destination: ComponentRegistry[AnyRef], registries: ComponentRegistry[_ <: AnyRef]*) {
    def wrapSecurityFilter(filter: AnyRef) = {
      if (isSecurityFilter(filter)) createSecurityChain(List(filter))
      else filter
    }

    for {
      registry <- registries
      (id, filter) <- registry.allComponentsById()
    } destination.register(id, wrapSecurityFilter(filter))
  }

  private def addAllChains(destination: ComponentRegistry[AnyRef], chainsConfig: ChainsConfig, filters: ComponentRegistry[_ <: AnyRef]*) {
    val chainRegistry = buildChainsRegistry(chainsConfig, filters)

    for (chain <- chainRegistry.allComponents()) {
      destination.register(chain.getId, toJDiscChain(chain))
    }
  }


  def buildChainsRegistry(chainsConfig: ChainsConfig, filters: Seq[ComponentRegistry[_ <: AnyRef]]) = {
    val chainRegistry = new ChainRegistry[FilterWrapper]
    val chainsModel = ChainsModelBuilder.buildFromConfig(chainsConfig)

    ChainsConfigurer.prepareChainRegistry(chainRegistry, chainsModel, allFiltersWrapped(filters: _*))
    chainRegistry.freeze()
    chainRegistry
  }

  private def toJDiscChain(chain: Chain[FilterWrapper]): AnyRef = {
    checkFilterTypesCompatible(chain)
    val jDiscFilters = chain.components() map {_.filter}

    wrapJDiscChain(wrapSecurityFilters(jDiscFilters.toList))
  }

  def wrapJDiscChain(filters: List[AnyRef]): AnyRef = {
    if (filters.size == 1) filters.head
    else {
      filters.head match {
        case _: RequestFilter  => RequestFilterChain.newInstance(filters.asInstanceOf[List[RequestFilter]])
        case _: ResponseFilter => ResponseFilterChain.newInstance(filters.asInstanceOf[List[ResponseFilter]])
      }
    }
  }

  def wrapSecurityFilters(filters: List[AnyRef]): List[AnyRef] = {
    if (filters.isEmpty) List()
    else {
      val (securityFilters, rest) = filters.span(isSecurityFilter)
      if (securityFilters.isEmpty) {
        val (regularFilters, rest) = filters.span(!isSecurityFilter(_))
        regularFilters ++ wrapSecurityFilters(rest)
      } else {
        createSecurityChain(securityFilters) :: wrapSecurityFilters(rest)
      }
    }
  }

  def createSecurityChain(filters: List[AnyRef]): AnyRef = {
    filters.head match {
      case _: SecurityRequestFilter  => SecurityRequestFilterChain.newInstance(filters.asInstanceOf[List[SecurityRequestFilter]])
      case _: SecurityResponseFilter => SecurityResponseFilterChain.newInstance(filters.asInstanceOf[List[SecurityResponseFilter]])
      case _ => throw new IllegalArgumentException("Unexpected class " + filters.head.getClass)
    }
  }

  def isSecurityFilter(filter: AnyRef) = {
    filter match {
      case _: SecurityRequestFilter => true
      case _: SecurityResponseFilter => true
      case _ => false
    }
  }

  def checkFilterTypesCompatible(chain: Chain[FilterWrapper]) {
    val requestFilters  = Set[Class[_]](classOf[RequestFilter], classOf[SecurityRequestFilter])
    val responseFilters = Set[Class[_]](classOf[ResponseFilter], classOf[SecurityResponseFilter])

    def check(a: FilterWrapper, b: FilterWrapper) {
      if (requestFilters(a.filterType) && responseFilters(b.filterType))
        throw new RuntimeException("Can't mix request and response filters in chain %s: %s, %s".format(chain.getId, a.getId, b.getId))
    }

    overlappingPairIterator(chain.components).foreach {
      case Seq(_) =>
      case Seq(filter1: FilterWrapper, filter2: FilterWrapper) =>
        check(filter1, filter2)
        check(filter2, filter1)
    }
  }

  def overlappingPairIterator[T](s: Seq[T]) = s.iterator.sliding(2, 1)
}
