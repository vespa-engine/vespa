// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone

import com.yahoo.config.model.producer.AbstractConfigProducerRoot
import com.yahoo.config.model.test.MockRoot
import com.yahoo.container.Container
import com.yahoo.jdisc.test.TestDriver
import scala.xml.Node
import com.yahoo.vespa.model.VespaModel
import com.yahoo.io.IOUtils
import java.nio.file.{Files, Path}
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder.Networking

/**
 * Creates a local application from vespa-services fragments.
 *
 * @author  tonytv
 */
object StandaloneContainer {
  def firstContainerId(root: AbstractConfigProducerRoot): String = {
    root.getConfigProducer("container").get().getConfigId
  }

  def withStandaloneContainer[T](containerNode: Node) {
    withTempDirectory { applicationDirectory =>
      System.setProperty(StandaloneContainerApplication.applicationLocationInstallVariable, applicationDirectory.toString)
      createServicesXml(applicationDirectory, containerNode)

      val driver = TestDriver.newInjectedApplicationInstance(classOf[StandaloneContainerApplication])
      driver.close()
      Container.resetInstance()
    }
  }

  def withContainerModel[T](containerNode: Node)(f: VespaModel => T) {
    withTempDirectory { applicationPath =>
      createServicesXml(applicationPath, containerNode)

      val distributedFiles = new LocalFileDb(applicationPath)
      val (root, container) = StandaloneContainerApplication.createContainerModel(
        applicationPath,
        distributedFiles,
        applicationPath.resolve("preprocesedApp").toFile,
        networkingOption = Networking.enable)
      f(root)
    }
  }

  private def withTempDirectory[T](f : Path => T) : T = {
    val directory = Files.createTempDirectory("application")
    try {
      f(directory)
    } finally {
      IOUtils.recursiveDeleteDir(directory.toFile)
    }
  }

  private def createServicesXml(applicationPath : Path,
                             containerNode: Node) {

    scala.xml.XML.save(applicationPath.resolve("services.xml").toFile.getAbsolutePath,
      containerNode, xmlDecl = true)
  }
}
