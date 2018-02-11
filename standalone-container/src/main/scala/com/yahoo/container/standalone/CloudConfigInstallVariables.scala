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
class CloudConfigInstallVariables extends CloudConfigOptions {
  import CloudConfigInstallVariables._

  override val rpcPort = optionalInstallVar[Integer]("port_configserver_rpc", "services")
  override val allConfigServers = installVar("addr_configserver", "services") withDefault Array[ConfigServer]()
  override val multiTenant = optionalInstallVar[java.lang.Boolean]("multitenant")

  override val zookeeperBarrierTimeout = optionalInstallVar[java.lang.Long]("zookeeper_barrier_timeout")
  override val sessionLifeTimeSecs = optionalInstallVar[java.lang.Long]("session_lifetime")
  override val configModelPluginDirs = installVar("config_model_plugin_dirs") withDefault Array[String]()
  override val zookeeperClientPort = optionalInstallVar[Integer]("zookeeper_clientPort")
  override val zookeeperQuorumPort = optionalInstallVar[Integer]("zookeeper_quoromPort")
  override val zookeeperElectionPort = optionalInstallVar[Integer]("zookeeper_electionPort")
  override val payloadCompressionType = optionalInstallVar[java.lang.String]("payload_compression_type")
  override val environment = optionalInstallVar[java.lang.String]("environment")
  override val region = optionalInstallVar[java.lang.String]("region")
  override val system = optionalInstallVar[java.lang.String]("system")
  override val defaultFlavor = optionalInstallVar[java.lang.String]("default_flavor")
  override val defaultAdminFlavor = optionalInstallVar[java.lang.String]("default_admin_flavor")
  override val defaultContainerFlavor = optionalInstallVar[java.lang.String]("default_container_flavor")
  override val defaultContentFlavor = optionalInstallVar[java.lang.String]("default_content_flavor")
  override val useVespaVersionInRequest = optionalInstallVar[java.lang.Boolean]("use_vespa_version_in_request")
  override val hostedVespa = optionalInstallVar[java.lang.Boolean]("hosted_vespa")
  override val numParallelTenantLoaders = optionalInstallVar[java.lang.Integer]("num_parallel_tenant_loaders")
  override val dockerRegistry = optionalInstallVar[java.lang.String]("docker_registry")
  override val dockerVespaBaseImage = optionalInstallVar[java.lang.String]("docker_vespa_base_image")
  override val loadBalancerAddress = optionalInstallVar[java.lang.String]("load_balancer_address")
}

object CloudConfigInstallVariables {
  private class InstallVariable(installPkg:String, name: String) {
    val value = Environment.optionalInstallVariable(installPkg + "." + name)

    def withDefault[T](defaultValue: T)(implicit c: Converter[T]) : T = {
      value map { implicitly[Converter[T]].convert } getOrElse defaultValue
    }
  }

  private def installVar(setting:String, installPkg: String = "cloudconfig_server") = new InstallVariable(installPkg, setting)

  private def optionalInstallVar[T](setting:String, installPkg: String = "cloudconfig_server")(implicit c: Converter[T]): Optional[T] = {
    Environment.optionalInstallVariable(installPkg + "." + setting) map ( c.convert )
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
