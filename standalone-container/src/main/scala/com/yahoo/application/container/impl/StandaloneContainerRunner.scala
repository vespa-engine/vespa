// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.impl

import java.nio.file.Files
import com.yahoo.text.Utf8

/**
 * @author tonytv
 */
final class StandaloneContainerRunner {



}

object StandaloneContainerRunner {
  def createApplicationPackage(servicesXml: String) = {
    val applicationDir = Files.createTempDirectory("application")

    val servicesXmlFile = applicationDir.resolve("services.xml");
    var content = servicesXml;
    if ( ! servicesXml.startsWith("<?xml"))
        content = """<?xml version="1.0" encoding="utf-8" ?>""" + '\n' + servicesXml
    Files.write(servicesXmlFile, Utf8.toBytes(content))
    applicationDir
  }
}
