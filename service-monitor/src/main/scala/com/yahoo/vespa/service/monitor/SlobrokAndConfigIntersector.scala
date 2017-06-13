// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor

import java.util.{Collections, Optional}
import java.util.logging.Logger

import com.google.inject.Inject
import com.yahoo.cloud.config.ConfigserverConfig
import com.yahoo.component.AbstractComponent
import com.yahoo.config.subscription.ConfigSourceSet
import com.yahoo.log.LogLevel
import com.yahoo.vespa.applicationmodel._
import com.yahoo.vespa.config.GenerationCounter
import com.yahoo.vespa.service.monitor.SlobrokAndConfigIntersector._
import com.yahoo.vespa.service.monitor.SlobrokMonitor._
import com.yahoo.vespa.service.monitor.config.InstancesObservables
import com.yahoo.vespa.service.monitor.config.InstancesObservables._
import rx.lang.scala.{Subscription, Observable}

import scala.collection.convert.decorateAsJava._
import scala.collection.convert.decorateAsScala._
import scala.collection.immutable.Set
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Quick and dirty intersection of slobrok and model config.
 * @author tonytv
 */
 // TODO: This class is the API of the service monitor. It should have a proper name rather than be named after an implementation detail
 // TODO: For the same reason, add javadoc
class SlobrokAndConfigIntersector(
  configSourceSet: ConfigSourceSet,
  //Config servers that are part of an application instance, i.e. not multi-tenant, are included in lb-services config
  multiTenantConfigServerHostNames: Set[HostName],
  configCounter: GenerationCounter
  ) extends AbstractComponent with ServiceMonitor
{
  private val instancesObservables = new InstancesObservables(configSourceSet)

  @Inject
  def this(config: ConfigserverConfig, configCounter: GenerationCounter) = {
    this(
      new ConfigSourceSet(s"tcp/localhost:${config.rpcport()}"),
      if (config.multitenant()) configServerHostNames(config) else Set(),
      configCounter)
  }

  @volatile
  private var latestSlobrokMonitorMap: Map[ApplicationInstanceReference, SlobrokMonitor] = Map()

  private val zoneConfigServerCluster: Map[ApplicationInstanceReference, ApplicationInstance[Void]] =
    if (multiTenantConfigServerHostNames.isEmpty) Map()
    else Map(
      new ApplicationInstanceReference(syntheticHostedVespaTenantId, configServerApplicationInstanceId ) ->
        new ApplicationInstance[Void](
          syntheticHostedVespaTenantId,
          configServerApplicationInstanceId,
          Collections.singleton(new ServiceCluster[Void](
            new ClusterId("zone-config-servers"),
            SlobrokServiceNameUtil.configServerServiceType,
            configServer_ServerInstances(multiTenantConfigServerHostNames)
          ))))

  @Override
  def queryStatusOfAllApplicationInstances()
  : java.util.Map[ApplicationInstanceReference, ApplicationInstance[ServiceMonitorStatus]] =
  {
    val applicationInstanceMap: Map[ApplicationInstanceReference, ApplicationInstance[ServiceMonitorStatus]] = for {
      (applicationInstanceReference, applicationInstance) <- latestConfiguredServices()
    } yield {
      val slobrokMonitor = latestSlobrokMonitorMap.get(applicationInstanceReference)

      def monitoredStatus(serviceType: ServiceType, configId: ConfigId) = {
        SlobrokServiceNameUtil.serviceName(serviceType, configId) map { name =>
          if (slobrokMonitor.exists(_.isRegistered(name))) ServiceMonitorStatus.UP
          else ServiceMonitorStatus.DOWN
        } getOrElse ServiceMonitorStatus.NOT_CHECKED
      }

      val serviceClustersWithStatus = applicationInstance.serviceClusters.asScala.map { serviceCluster =>
        val serviceInstancesWithStatus = serviceCluster.serviceInstances().asScala.map { serviceInstance =>
          new ServiceInstance[ServiceMonitorStatus](
            serviceInstance.configId(),
            serviceInstance.hostName(),
            monitoredStatus(serviceCluster.serviceType, serviceInstance.configId))
        }
        new ServiceCluster[ServiceMonitorStatus](serviceCluster.clusterId(), serviceCluster.serviceType(), serviceInstancesWithStatus.asJava)
      }
      val applicationInstanceWithStatus: ApplicationInstance[ServiceMonitorStatus] = new ApplicationInstance(
        applicationInstanceReference.tenantId,
        applicationInstanceReference.applicationInstanceId,
        serviceClustersWithStatus.asJava)

      applicationInstanceReference -> applicationInstanceWithStatus
    }
    applicationInstanceMap.asJava
  }

  instancesObservables.slobroksPerInstance.subscribe { slobrokServiceMap =>
    val nextSlobrokMonitorMap = slobrokServiceMap.map { case (instanceReference, slobrokServices) =>
      val slobrokMonitor = latestSlobrokMonitorMap.getOrElse(instanceReference, new SlobrokMonitor())
      slobrokMonitor.setSlobrokConnectionSpecs(asConnectionSpecs(slobrokServices))
      (instanceReference, slobrokMonitor)
    }
    val removedSlobrokMonitors = (latestSlobrokMonitorMap -- nextSlobrokMonitorMap.keySet).values
    latestSlobrokMonitorMap = nextSlobrokMonitorMap
    removedSlobrokMonitors.foreach { _.shutdown() }
  }

  @volatile private var subscription: Option[Subscription] = None

  private val waitForConfig = Observable.interval(10 seconds)
    .map(ignored => configCounter.get()).filter(_ > 0).take(1).subscribe { _ =>
    subscription = Some(instancesObservables.connect())
  }

  Observable.interval(10 seconds).subscribe { _ =>
    val applicationInstances: Map[ApplicationInstanceReference, ApplicationInstance[ServiceMonitorStatus]] =
      queryStatusOfAllApplicationInstances().asScala.toMap
    logServiceStatus(applicationInstances)
  }

  object latestConfiguredServices {
    private val mostRecentServicesIterator =
      instancesObservables.servicesPerInstance.
        map(_ ++ zoneConfigServerCluster).
        toBlocking.
        mostRecent(initialValue = zoneConfigServerCluster).
        iterator

    def apply() = mostRecentServicesIterator.synchronized {
      mostRecentServicesIterator.next()
    }
  }

  override def deconstruct(): Unit = {
    waitForConfig.unsubscribe()
    subscription.foreach(sub => sub.unsubscribe())
  }
}

object SlobrokAndConfigIntersector {
  private val log = Logger.getLogger(getClass.getName)

  val syntheticHostedVespaTenantId = new TenantId("hosted-vespa")
  val configServerApplicationInstanceId = new ApplicationInstanceId("zone-config-servers")

  implicit class AsJavaOptional[T <: AnyRef](private val option: Option[T]) extends AnyVal {
    def asJava: Optional[T] = option match {
      case Some(v) => Optional.of(v)
      case None => Optional.empty()
    }
  }

  def selectFirst[A, B](a: A, b: B) = a

  private def convertSlobrokServicesToConfigIds(registeredServiceNames: Set[SlobrokServiceName]): Set[ConfigId] = {
    val registeredServiceNamesJavaSet: java.util.Set[String] = registeredServiceNames.map { _.s }.asJava
    val configIdsJavaSet: java.util.Set[String] = ServiceNameUtil.convertSlobrokServicesToConfigIds(registeredServiceNamesJavaSet)
    configIdsJavaSet.asScala.toSet.map { x: String => new ConfigId(x) }
  }

  private def logServiceStatus(instanceMap: Map[ApplicationInstanceReference, ApplicationInstance[ServiceMonitorStatus]]): Unit = {
    instanceMap.values.foreach(logServiceStatus)
  }

  private def logServiceStatus(applicationInstance: ApplicationInstance[ServiceMonitorStatus]): Unit =  {
    val serviceInstances =
      for {
        serviceCluster <- applicationInstance.serviceClusters.asScala
        serviceInstance <- serviceCluster.serviceInstances().asScala
      } yield serviceInstance

    val serviceInstancesGroupedByStatus = serviceInstances.groupBy(_.serviceStatus())

    def mkString(services: Traversable[ServiceInstance[ServiceMonitorStatus]]) = services.mkString("\t", "\n\t", "\n")

    log.log(LogLevel.DEBUG, s"For tenant ${applicationInstance.tenantId}, application instance ${applicationInstance.applicationInstanceId}\n" +
      serviceInstancesGroupedByStatus.map { case (monitoredStatus, serviceInstances) =>
          s"  $monitoredStatus\n" + mkString(serviceInstances)
      }.mkString("\n"))
  }

  private def asConnectionSpecs(slobroks: Traversable[SlobrokService]): Traversable[String] =
    slobroks map { case SlobrokService(hostName, port) => s"tcp/$hostName:$port" }

  private def configServerHostNames(config: ConfigserverConfig): Set[HostName] =
    //Each Zookeeper server in this config is started by a config server.
    //Each config server starts a single zookeeper server.
    config.zookeeperserver().asScala map {server => new HostName(server.hostname())} toSet

  private def configServer_ServerInstances(multiTenantConfigServerHostNames: Set[HostName])
  : java.util.Set[ServiceInstance[Void]] =
  {
    def serviceInstance(hostName: HostName) = new ServiceInstance[Void](
      new ConfigId("configId." + hostName.s),
      hostName,
      null)

    multiTenantConfigServerHostNames map serviceInstance asJava
  }
}
