// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen

import java.io.{FileNotFoundException, FileOutputStream, PrintStream, File}
import ConfigGenerator.{indentCode, createClassName}
import util.Random
import scala.collection.JavaConversions._

/**
 * Builds one Java class based on the given CNode tree.
 *
 * @author gjoranv
 * @author tonytv
 */
class JavaClassBuilder(
                        root: InnerCNode,
                        nd: NormalizedDefinition,
                        destDir: File)
  extends ClassBuilder
{
  import JavaClassBuilder._

  val javaPackage = PackagePrefix + root.getNamespace
  val className = createClassName(root.getName)

  override def createConfigClasses() {
    try {
      val outFile = new File(getDestPath(destDir, root.getNamespace), className + ".java")
      var out: PrintStream = null
      try {
        out = new PrintStream(new FileOutputStream(outFile))
        out.print(getConfigClass(className))
      } finally {
        if (out != null) out.close()
      }
      System.err.println(outFile.getPath + " successfully written.")
    }
    catch {
      case e: FileNotFoundException => {
        throw new CodegenRuntimeException(e)
      }
    }
  }

  def getConfigClass(className:String): String = {
    val ret = new StringBuilder

    ret.append(getHeader).append("\n\n")
    ret.append(getRootClassDeclaration(root, className)).append("\n\n")
    ret.append(indentCode(Indentation, getFrameworkCode(className))).append("\n\n")
    ret.append(ConfigGenerator.generateContent(Indentation, root)).append("\n")
    ret.append("}\n")

    ret.toString()
  }

  private def getHeader: String = {
    <code>
      |/**
      | * This file is generated from a config definition file.
      | * ------------   D O   N O T   E D I T !   ------------
      | */
      |
      |package {javaPackage};
      |
      |import java.util.*;
      |import java.nio.file.Path;
      |import edu.umd.cs.findbugs.annotations.NonNull;
      |{getImportFrameworkClasses(root.getNamespace)}
    </code>.text.stripMargin.trim
  }

  private def getImportFrameworkClasses(namespace: String): String = {
    if (namespace != CNode.DEFAULT_NAMESPACE)
      "import " + PackagePrefix + CNode.DEFAULT_NAMESPACE + ".*;\n"
    else
      ""
  }

  // TODO: remove the extra comment line " *" if root.getCommentBlock is empty
  private def getRootClassDeclaration(root:InnerCNode, className: String): String = {
      <code>
       |/**
       | * This class represents the root node of {root.getFullName}
       | *
       |{root.getCommentBlock(" *")} */
       |public final class {className} extends ConfigInstance {{
       |
       |  public final static String CONFIG_DEF_MD5 = "{root.getMd5}";
       |  public final static String CONFIG_DEF_NAME = "{root.getName}";
       |  public final static String CONFIG_DEF_NAMESPACE = "{root.getNamespace}";
       |  public final static String CONFIG_DEF_VERSION = "{root.getVersion}";
       |  public final static String[] CONFIG_DEF_SCHEMA = {{
       |{indentCode(Indentation * 2, getDefSchema)}
       |  }};
       |
       |  public static String getDefMd5()       {{ return CONFIG_DEF_MD5; }}
       |  public static String getDefName()      {{ return CONFIG_DEF_NAME; }}
       |  public static String getDefNamespace() {{ return CONFIG_DEF_NAMESPACE; }}
       |  public static String getDefVersion()   {{ return CONFIG_DEF_VERSION; }}
      </code>.text.stripMargin.trim
  }

  private def getDefSchema: String = {
    nd.getNormalizedContent.map { line =>
      "\"" +
        line.replace("\"", "\\\"") +
        "\""
    }.mkString(",\n")
  }

  private def getFrameworkCode(className: String): String = {
     getProducerBase
  }

  private def getProducerBase = {
    """
      |public interface Producer extends ConfigInstance.Producer {
      |  void getConfig(Builder builder);
      |}
    """.stripMargin.trim
  }
}


object JavaClassBuilder {
  private val PackagePrefix: String = System.getProperty("config.packagePrefix", "com.yahoo.")

  val Indentation = "  "

  /**
   * Returns a name that can be safely used as a local variable in the generated config class
   * for the given node. The name will be based on the given basis string, but the basis itself is
   * not a possible return value.
   *
   * @param node The node to find a unused symbol name for.
   * @param basis The basis for the generated symbol name.
   * @return A name that is not used in the given config node.
   */
  def createUniqueSymbol(node: CNode, basis: String) = {

    def getCandidate(cnt: Int) = {
      if (cnt < basis.length())
        basis.substring(0, cnt)
      else
        ReservedWords.INTERNAL_PREFIX + basis + Random.nextInt().abs
    }

    def getUsedSymbols: Set[String] = {
      (node.getChildren map (child => child.getName)).toSet
    }

    // TODO: refactoring potential
    val usedSymbols = getUsedSymbols
    var count = 1
    var candidate = getCandidate(count)
    while (usedSymbols contains(candidate)) {
      count += 1
      candidate = getCandidate(count)
    }
    candidate
  }

  /**
   * @param rootDir  The root directory for the destination path.
   * @param namespace  The namespace from the def file
   * @return the destination path for the generated config file, including the given rootDir.
   */
  private def getDestPath(rootDir: File, namespace: String): File = {
    var dir: File = rootDir
    val subDirs: Array[String] = (PackagePrefix + namespace).split("""\.""")
    for (subDir <- subDirs) {
      dir = new File(dir, subDir)
      this.synchronized {
        if (!dir.isDirectory && !dir.mkdir) throw new CodegenRuntimeException("Could not create " + dir.getPath)
      }
    }
    dir
  }
}
