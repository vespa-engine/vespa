// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.config

import java.util.concurrent.TimeUnit
import java.util.logging.{Level, Logger}

import com.yahoo.cloud.config.LbServicesConfig
import com.yahoo.cloud.config.LbServicesConfig.Tenants.Applications.Hosts
import com.yahoo.cloud.config.LbServicesConfig.Tenants.Applications.Hosts.Services.Ports
import com.yahoo.config.subscription.ConfigSourceSet
import com.yahoo.vespa.applicationmodel.{ApplicationInstance, ApplicationInstanceId, ApplicationInstanceReference, ClusterId, ConfigId, HostName, ServiceCluster, ServiceClusterKey, ServiceInstance, ServiceType, TenantId}
import com.yahoo.vespa.service.monitor.config.InstancesObservables._
import rx.lang.scala.JavaConversions._
import rx.lang.scala.{Observable, Subscription}
import rx.schedulers.Schedulers

import scala.collection.JavaConverters._
import scala.concurrent.duration._

/**
 * Provides streams of slobroks and services per application instance.
 * @author tonytv
 */
class InstancesObservables(configSourceSet: ConfigSourceSet) {
  private val lbServicesConfigObservable =
    toScalaObservable(ConfigObservableCreator.
      create(configSourceSet, classOf[LbServicesConfig], "*")).
      retryWhen(
        _.flatMap { throwable =>
          val delay = Duration(30, TimeUnit.SECONDS)
          log.log(Level.WARNING, s"Subscription to LbServicesConfig failed, sources=$configSourceSet. Retrying in $delay", throwable)
          Observable.timer(delay)
        }).
      map(asInstanceReferenceToHostConfigMap).
      subscribeOn(Schedulers.io()).
      publish


  val slobroksPerInstance: Observable[Map[ApplicationInstanceReference, Traversable[SlobrokService]]] =
    lbServicesConfigObservable.map { _.mapValues(extractSlobrokServices).toMap  }


  val servicesPerInstance: Observable[Map[ApplicationInstanceReference, ApplicationInstance[Void]]] =
    lbServicesConfigObservable.map( _.map { case (applicationInstanceReference, x) =>
      val serviceClusters: java.util.Set[ServiceCluster[Void]] = asServiceClusterSet(x)
      val applicationInstance = new ApplicationInstance(
        applicationInstanceReference.tenantId,
        applicationInstanceReference.applicationInstanceId,
        serviceClusters)
      applicationInstanceReference -> applicationInstance
    }.toMap)

  def connect(): Subscription = lbServicesConfigObservable.connect
}

object InstancesObservables {
  private val log = Logger.getLogger(getClass.getName)

  case class SlobrokService(hostName: String, port: Integer)



  private def asInstanceReferenceToHostConfigMap(config: LbServicesConfig) = {
    for {
      (tenantIdString, tenantConfig) <- config.tenants().asScala
      (applicationIdString, applicationConfig) <- tenantConfig.applications().asScala
    } yield {
      val applicationInstanceReference: ApplicationInstanceReference = new ApplicationInstanceReference(
        new TenantId(tenantIdString),
        new ApplicationInstanceId(applicationIdString))

      (applicationInstanceReference, applicationConfig.hosts())
    }
  }

  private def extractSlobrokServices(hostsConfigs: java.util.Map[String, Hosts]): Traversable[SlobrokService] = {
    def rpcPort(ports: Traversable[Ports]) = ports.collectFirst {
      case port if port.tags().contains("rpc") => port.number()
    }

    for {
      (hostName, hostConfig) <- hostsConfigs.asScala
      slobrokService <- Option(hostConfig.services("slobrok"))
    } yield SlobrokService(
      hostName,
      rpcPort(slobrokService.ports().asScala).getOrElse(throw new RuntimeException("Found slobrok without rpc port")))
  }

  private def asServiceClusterSet(hostsConfigs: java.util.Map[String, Hosts])
  : java.util.Set[ServiceCluster[Void]] = {

    val serviceInstancesGroupedByCluster: Map[ServiceClusterKey, Iterable[ServiceInstance[Void]]] = (for {
      (hostName, hostConfig) <- hostsConfigs.asScala.view
      (serviceName, servicesConfig) <- hostConfig.services().asScala
    } yield {
      (new ServiceClusterKey(new ClusterId(servicesConfig.clustername()), new ServiceType(servicesConfig.`type`())),
        new ServiceInstance(new ConfigId(servicesConfig.configId()), new HostName(hostName), null.asInstanceOf[Void]))
    }).groupByKeyWithValue(_._1, _._2)

    val serviceClusterSet: Set[ServiceCluster[Void]] = serviceInstancesGroupedByCluster.map {
      case (serviceClusterKey, serviceInstances) =>
        new ServiceCluster(
          serviceClusterKey.clusterId,
          serviceClusterKey.serviceType,
          serviceInstances.toSet.asJava)
    }.toSet

    serviceClusterSet.asJava
  }
  
  implicit class IterableWithImprovedGrouping[A](val iterable: Iterable[A]) {
    def groupByKeyWithValue[K, V](keyExtractor: (A) => K, valueExtractor: (A) => V): Map[K, Iterable[V]] = {
      val groupedByKey: Map[K, Iterable[A]] = iterable.groupBy(keyExtractor)
      groupedByKey.mapValues(_.map(valueExtractor))
    }
  }
}
