// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

import java.util.Optional

import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions.ConfigServer

import scala.language.implicitConversions
import scala.util.Try

/**
  * @author Tony Vaagenes
  */
class CloudConfigYinstVariables extends CloudConfigOptions {
  import CloudConfigYinstVariables._

  override val rpcPort = optionalYinstVar[Integer]("port_configserver_rpc", "services")
  override val allConfigServers = yinstVar("addr_configserver", "services") withDefault Array[ConfigServer]()
  override val multiTenant = optionalYinstVar[java.lang.Boolean]("multitenant")

  override val zookeeperBarrierTimeout = optionalYinstVar[java.lang.Long]("zookeeper_barrier_timeout")
  override val sessionLifeTimeSecs = optionalYinstVar[java.lang.Long]("session_lifetime")
  override val configModelPluginDirs = yinstVar("config_model_plugin_dirs") withDefault Array[String]()
  override val zookeeperClientPort = optionalYinstVar[Integer]("zookeeper_clientPort")
  override val zookeeperQuorumPort = optionalYinstVar[Integer]("zookeeper_quoromPort")
  override val zookeeperElectionPort = optionalYinstVar[Integer]("zookeeper_electionPort")
  override val payloadCompressionType = optionalYinstVar[java.lang.String]("payload_compression_type")
  override val environment = optionalYinstVar[java.lang.String]("environment")
  override val region = optionalYinstVar[java.lang.String]("region")
  override val system = optionalYinstVar[java.lang.String]("system")
  override val defaultFlavor = optionalYinstVar[java.lang.String]("default_flavor")
  override val defaultAdminFlavor = optionalYinstVar[java.lang.String]("default_admin_flavor")
  override val defaultContainerFlavor = optionalYinstVar[java.lang.String]("default_container_flavor")
  override val defaultContentFlavor = optionalYinstVar[java.lang.String]("default_content_flavor")
  override val useVespaVersionInRequest = optionalYinstVar[java.lang.Boolean]("use_vespa_version_in_request")
  override val hostedVespa = optionalYinstVar[java.lang.Boolean]("hosted_vespa")
  override val numParallelTenantLoaders = optionalYinstVar[java.lang.Integer]("num_parallel_tenant_loaders")
  override val dockerRegistry = optionalYinstVar[java.lang.String]("docker_registry")
  override val dockerVespaBaseImage = optionalYinstVar[java.lang.String]("docker_vespa_base_image")
  override val loadBalancerAddress = optionalYinstVar[java.lang.String]("load_balancer_address")
}

object CloudConfigYinstVariables {
  private class YinstVariable(yinstPkg:String, name: String) {
    val value = Environment.optionalYinstVariable(yinstPkg + "." + name)

    def withDefault[T](defaultValue: T)(implicit c: Converter[T]) : T = {
      value map { implicitly[Converter[T]].convert } getOrElse defaultValue
    }
  }

  private def yinstVar(setting:String, yinstPkg: String = "cloudconfig_server") = new YinstVariable(yinstPkg, setting)

  private def optionalYinstVar[T](setting:String, yinstPkg: String = "cloudconfig_server")(implicit c: Converter[T]): Optional[T] = {
    Environment.optionalYinstVariable(yinstPkg + "." + setting) map ( c.convert )
  }

  implicit val configServerConverter: Converter[Array[ConfigServer]] = new Converter[Array[ConfigServer]] {
    override def convert(s: String) = {
      s split "[, ]" filter { !_.isEmpty } map { toConfigServer }
    }
  }

  implicit val stringArrayConverter: Converter[Array[String]] = new Converter[Array[String]] {
    override def convert(s: String) = {
      s split "[, ]" filter { !_.isEmpty }
    }
  }

  private def toConfigServer(hostPort: String): ConfigServer = Try {
    val (host, portStr) = splitFirst(hostPort, ':')
    val port = portStr map { _.toInt }
    new ConfigServer(host, port)
  }.getOrElse(throw new IllegalArgumentException(s"Invalid config server '$hostPort'"))

  private def splitFirst(string: String, separator: Character): (String, Option[String]) = {
    val (beginning, endWithSeparator) = string span { _ != separator }
    (beginning, tailOption(endWithSeparator))
  }

  def tailOption(s: String) = {
    if (s.isEmpty) None
    else Some(s.tail)
  }

  implicit def toJavaOptional[U <% V, V](option: Option[U]): Optional[V] = option match {
    case Some(u) => Optional.of(u: V)
    case None => Optional.empty()
  }
}
