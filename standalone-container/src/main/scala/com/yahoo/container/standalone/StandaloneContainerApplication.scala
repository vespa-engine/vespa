// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

import java.io.{File, IOException}
import java.lang.{Boolean => JBoolean}
import java.nio.file.{FileSystems, Files, Path, Paths}

import com.google.inject.name.Names
import com.google.inject.{AbstractModule, Inject, Injector, Key}
import com.yahoo.collections.CollectionUtil.first
import com.yahoo.config.application.api.{ApplicationPackage, FileRegistry}
import com.yahoo.config.model.application.provider._
import com.yahoo.config.model.builder.xml.XmlHelper
import com.yahoo.config.model.deploy.DeployState
import com.yahoo.config.model.{ApplicationConfigProducerRoot, ConfigModelRepo}
import com.yahoo.config.provision.Zone
import com.yahoo.container.di.config.SubscriberFactory
import com.yahoo.container.jdisc.ConfiguredApplication
import com.yahoo.container.standalone.Environment._
import com.yahoo.container.standalone.StandaloneContainerApplication._
import com.yahoo.io.IOUtils
import com.yahoo.jdisc.application.Application
import com.yahoo.text.XML
import com.yahoo.vespa.defaults.Defaults
import com.yahoo.vespa.model.VespaModel
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder
import com.yahoo.vespa.model.container.{Container, ContainerModel}
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking
import com.yahoo.vespa.model.container.xml.{ConfigServerContainerModelBuilder, ContainerModelBuilder}
import org.w3c.dom.Element

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
class StandaloneContainerApplication @Inject()(injector: Injector) extends Application {

  ConfiguredApplication.ensureVespaLoggingInitialized()

  val applicationPath: Path = injectedApplicationPath.getOrElse(installApplicationPath)

  val distributedFiles = new LocalFileDb(applicationPath)

  val configModelRepo = Try { injector.getInstance(Key.get(classOf[ConfigModelRepo], configModelRepoName))}.getOrElse(new ConfigModelRepo)

  val networkingOption = Try {
    injector.getInstance(Key.get(classOf[JBoolean], Names.named(disableNetworkingAnnotation)))
  }.map  {
    case JBoolean.TRUE => Networking.disable
    case JBoolean.FALSE => Networking.enable
  }.getOrElse(Networking.enable)

  val (modelRoot, container) = withTempDir(
    preprocessedApplicationDir => createContainerModel(applicationPath, distributedFiles, preprocessedApplicationDir, networkingOption, configModelRepo))

  val configuredApplication = createConfiguredApplication(container)

  def createConfiguredApplication(container: Container): Application = {
    val augmentedInjector = injector.createChildInjector(new AbstractModule {
      def configure() {
        bind(classOf[SubscriberFactory]).toInstance(new StandaloneSubscriberFactory(modelRoot))
      }
    })

    System.setProperty("config.id", container.getConfigId) //TODO: DRY
    augmentedInjector.getInstance(classOf[ConfiguredApplication])
  }

  def injectedApplicationPath = Try {
    injector.getInstance(Key.get(classOf[Path], applicationPathName))
  }.toOption

  def installApplicationPath = path(installVariable(applicationLocationInstallVariable))

  override def start() {
    try {
      com.yahoo.container.Container.get().setCustomFileAcquirer(distributedFiles)
      configuredApplication.start()
    }
    catch {
      case e: Exception => { com.yahoo.container.Container.resetInstance(); throw e; }
    }
  }

  override def stop() {
    configuredApplication.stop()
  }

  override def destroy() {
    com.yahoo.container.Container.resetInstance()
    configuredApplication.destroy()
  }
}

object StandaloneContainerApplication {
  val packageName = "standalone_jdisc_container"
  val applicationLocationInstallVariable = s"$packageName.app_location"
  val deploymentProfileInstallVariable = s"$packageName.deployment_profile"

  val applicationPathName = Names.named(applicationLocationInstallVariable)

  val disableNetworkingAnnotation = "JDisc.disableNetworking"
  val configModelRepoName = Names.named("ConfigModelRepo")
  val configDefinitionRepo = new StaticConfigDefinitionRepo()

  val defaultTmpBaseDir = Defaults.getDefaults().underVespaHome("tmp")
  val tmpDirName = "standalone_container"

  private def withTempDir[T](f: File => T): T = {
    val tmpDir = createTempDir()
    try {
      f(tmpDir)
    } finally {
      IOUtils.recursiveDeleteDir(tmpDir)
    }
  }

  private def createTempDir(): File = {
    def getBaseDir: Path = {
      val tmpBaseDir =
        if (new File(defaultTmpBaseDir).exists())
          defaultTmpBaseDir
        else
          System.getProperty("java.io.tmpdir")

      Paths.get(tmpBaseDir)
    }

    val basePath: Path = getBaseDir
    val tmpDir = Files.createTempDirectory(basePath, tmpDirName)
    tmpDir.toFile
  }

  private def validateApplication(applicationPackage: ApplicationPackage) = {
    try {
      applicationPackage.validateXML()
    } catch {
      case e: IOException => throw new IllegalArgumentException(e)
    }
  }

  def newContainerModelBuilder(networkingOption: Networking): ContainerModelBuilder = {
    optionalInstallVariable(deploymentProfileInstallVariable) match {
      case None => new ContainerModelBuilder(true, networkingOption)
      case Some("configserver") => new ConfigServerContainerModelBuilder(new CloudConfigInstallVariables)
      case profileName => throw new RuntimeException(s"Invalid deployment profile '$profileName'")
    }
  }

  def createContainerModel(applicationPath: Path,
                           fileRegistry: FileRegistry,
                           preprocessedApplicationDir: File,
                           networkingOption: Networking,
                           configModelRepo: ConfigModelRepo = new ConfigModelRepo): (VespaModel, Container) = {
    val logger = new BaseDeployLogger
    val rawApplicationPackage = new FilesApplicationPackage.Builder(applicationPath.toFile).includeSourceFiles(true).preprocessedDir(preprocessedApplicationDir).build()
    val applicationPackage = rawApplicationPackage.preprocess(Zone.defaultZone(), logger)
    validateApplication(applicationPackage)
    val deployState = new DeployState.Builder().
      applicationPackage(applicationPackage).
      fileRegistry(fileRegistry).
      deployLogger(logger).
      configDefinitionRepo(configDefinitionRepo).
      build()

    val root = VespaModel.createIncomplete(deployState)
    val vespaRoot = new ApplicationConfigProducerRoot(root,
      "vespa",
      deployState.getDocumentModel,
      deployState.getProperties.vespaVersion(),
      deployState.getProperties.applicationId())
    

    val spec = containerRootElement(applicationPackage)
    val containerModel = newContainerModelBuilder(networkingOption).build(deployState, configModelRepo, vespaRoot, spec)
    containerModel.getCluster().prepare()
    DeprecationSuppressor.initializeContainerModel(containerModel, configModelRepo)
    val container = first(containerModel.getCluster().getContainers)

    // TODO: If we can do the mutations below on the builder, we can separate out model finalization from the
    // VespaModel constructor, such that the above and below code to finalize the container can be
    // replaced by root.finalize();

    // Always disable rpc server for standalone container. This server will soon be removed anyway.
    container.setRpcServerEnabled(false)
    container.setHttpServerEnabled(networkingOption == Networking.enable)

    initializeContainer(container, spec)

    root.freezeModelTopology()
    (root, container)
  }

  def initializeContainer(container: Container, spec: Element) {
    val host = container.getRoot.getHostSystem.getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC)

    container.setBasePort(VespaDomBuilder.getXmlWantedPort(spec))
    container.setHostResource(host)
    container.initService()
  }

  def getJDiscInServices(element: Element): Element = {
    def nameAndId(elements: List[Element]): List[String] = {
      elements map { e => s"${e.getNodeName} id='${e.getAttribute("id")}'" }
    }

    val jDiscElements = ContainerModelBuilder.configModelIds.asScala flatMap { name => XML.getChildren(element, name.getName).asScala }
    jDiscElements.toList match {
      case List(e) => e
      case Nil  => throw new RuntimeException("No jdisc element found under services.")
      case multipleElements: List[Element] => throw new RuntimeException("Found multiple JDisc elements: " + nameAndId(multipleElements).mkString(", "))
    }
  }

  def containerRootElement(applicationPackage: ApplicationPackage) : Element = {
    val element = XmlHelper.getDocument(applicationPackage.getServices).getDocumentElement
    val nodeName = element.getNodeName

    if (ContainerModelBuilder.configModelIds.asScala.map(_.getName).contains(nodeName)) element
    else getJDiscInServices(element)
  }

  def path(s: String) = FileSystems.getDefault.getPath(s)

  // Ugly hack required since Scala cannot suppress warnings. https://issues.scala-lang.org/browse/SI-7934
  private object DeprecationSuppressor extends DeprecationSuppressor
  @deprecated("", "")
  private class DeprecationSuppressor {
    def initializeContainerModel(containerModel: ContainerModel, configModelRepo: ConfigModelRepo): Unit = {
      containerModel.initialize(configModelRepo)
    }
  }
}
