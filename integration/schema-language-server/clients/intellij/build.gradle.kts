plugins {
  id("java")
  id("org.jetbrains.kotlin.jvm") version "1.9.24"
  id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "ai.vespa.schemals"
version = File("VERSION").inputStream().readBytes().toString(Charsets.UTF_8).trim()
val JAVA_VERSION = "17"

repositories {
  mavenCentral()

  maven {
    url = uri("https://repo.eclipse.org/content/repositories/lemminx")
    metadataSources {
      mavenPom()
      artifact()
    }
  }

  mavenLocal()
  maven {
    url = uri("file://${System.getProperty("user.home")}/.m2/repository")
    metadataSources {
      mavenPom()
      artifact()
    }
  }

  intellijPlatform {
    defaultRepositories()
  }
}

dependencies {
  implementation("com.yahoo.vespa:config-model:8-SNAPSHOT")
  implementation("com.yahoo.vespa:searchlib:8-SNAPSHOT")
  implementation("com.yahoo.vespa:container-search:8-SNAPSHOT")
  implementation("com.yahoo.vespa:config-model-api:8-SNAPSHOT")
  implementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
  implementation("org.junit.jupiter:junit-jupiter-engine:5.8.2")
  implementation("org.jsoup:jsoup:1.17.2")
  implementation("com.vladsch.flexmark:flexmark-html2md-converter:0.64.8")

  // Note: its quite important we ignore lsp4j, as the classes would collide
  //       with the lsp4ij plugins classes.
  implementation("org.eclipse.lemminx:org.eclipse.lemminx:0.28.0") {
    exclude(group = "org.eclipse.lsp4j")
    exclude(group = "com.google.code.gson")
  }

  intellijPlatform {
    intellijIdeaCommunity("2024.2")
    instrumentationTools()
    plugin("com.redhat.devtools.lsp4ij:0.7.0")
  }
}

intellijPlatform {
  pluginConfiguration {
    name = "Vespa Schema Language Support"
  }
}

java.sourceSets["main"].java {
  srcDir("../../language-server/src")
  srcDir("../../language-server/target/generated-sources/ccc/")
}

interface InjectFileSystem {
  @get:Inject val fs: FileSystemOperations
}

tasks {
  // Set the JVM compatibility versions
  withType<JavaCompile> {
    sourceCompatibility = JAVA_VERSION 
    targetCompatibility = JAVA_VERSION 
  }
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = JAVA_VERSION
  }

  prepareSandbox {
    val fromPathSchema = "../../language-server/target/schema-language-server-jar-with-dependencies.jar"
    val fromPathLemminx = "../../lemminx-vespa/target/lemminx-vespa-jar-with-dependencies.jar"
    val toPath = pluginDirectory.get()

    // see: https://docs.gradle.org/8.7/userguide/configuration_cache.html#config_cache:requirements:disallowed_types
    val injected = project.objects.newInstance<InjectFileSystem>()
    doLast {
      injected.fs.copy {
        from(fromPathSchema)
        from(fromPathLemminx)
        into(toPath)
      }
    }
  }

  patchPluginXml {
    sinceBuild.set("232")
    untilBuild.set(provider { null })
  }

  signPlugin {
    certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
    privateKey.set(System.getenv("PRIVATE_KEY"))
    password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
  }

  publishPlugin {
    token.set(System.getenv("PUBLISH_TOKEN"))
  }
}
