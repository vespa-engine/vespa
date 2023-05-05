// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask

plugins {
  id("java-library")
  id("org.jetbrains.intellij") version "1.13.3"
  id("org.jetbrains.grammarkit") version "2022.3.1"
  id("maven-publish") // to deploy the plugin into a Maven repo
}

group="ai.vespa"
version="1.4.0" // Also update pom.xml version AND the version below if this is changed

defaultTasks("buildPlugin")

apply(plugin="org.jetbrains.grammarkit")

task<GenerateLexerTask>("generateSdLexer") {
  sourceFile.set(file("src/main/jflex/ai/vespa/intellij/schema/lexer/sd.flex"))
  targetDir.set("target/generated-sources/jflex/ai/vespa/intellij/schema/lexer/")
  targetClass.set("SdLexer")
  purgeOldFiles.set(true)
}

task<GenerateParserTask>("generateSdParser") {
  sourceFile.set(file("src/main/bnf/ai/vespa/intellij/schema/parser/sd.bnf"))
  targetRoot.set("target/generated-sources/bnf/")
  pathToParser.set("ai/vespa/intellij/schema/parser/SdParser.java")
  pathToPsiRoot.set("ai/vespa/intellij/schema/parser/psi/")
  purgeOldFiles.set(true)
}

repositories {
  mavenCentral()
}

sourceSets {
  main {
    java.srcDirs("src/main/java", "target/generated-sources/bnf", "target/generated-sources/jflex")
  }
}

// See https://github.com/JetBrains/gradle-intellij-plugin/
intellij {
  version.set("2023.1")
}

tasks {

  compileJava {
    dependsOn("generateSdLexer", "generateSdParser")
    sourceCompatibility = "17"
    targetCompatibility = "17"
  }

  patchPluginXml {
    version.set("1.4.0") // TODO: Use one version property
    sinceBuild.set("231")
    // Appears on the plugin page in preferences/plugins
    changeNotes.set("""
       Support for IntelliJ 2023
    """)
  }

  test {
    useJUnitPlatform()
  }

}

