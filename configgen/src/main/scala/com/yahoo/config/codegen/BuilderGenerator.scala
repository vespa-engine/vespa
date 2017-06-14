// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen

import com.yahoo.config.codegen.ReservedWords.{INTERNAL_PREFIX => InternalPrefix}
import JavaClassBuilder.{Indentation, createUniqueSymbol}
import ConfigGenerator.{indentCode, nodeClass, userDataType, boxedDataType}
import com.yahoo.config.codegen.LeafCNode._

/**
 * @author gjoranv
 */

object BuilderGenerator {

  def getBuilder(node: InnerCNode): String = {
    getDeclaration(node) + "\n" +
      indentCode(Indentation,
        getUninitializedScalars(node) + "\n\n" +
          node.getChildren.map(getBuilderFieldDefinition).mkString("\n") + "\n\n" +
          getBuilderConstructors(node, nodeClass(node)) + "\n\n" +
          getOverrideMethod(node) + "\n\n" +
          getBuilderSetters(node) + "\n" +
          getSpecialRootBuilderCode(node)
      ) +
      "}"
  }

  private def getDeclaration(node: InnerCNode) = {
    def getInterfaces =
      if (node.getParent == null) "implements ConfigInstance.Builder"
      else "implements ConfigBuilder"

    "public static class Builder " + getInterfaces + " {"
  }

  private def getSpecialRootBuilderCode(node: InnerCNode) = {
    if (node.getParent == null) "\n" + getDispatchCode(node) + "\n"
    else ""
  }

  private def getDispatchCode(node: InnerCNode) = {
    // Use full path to @Override, as users are free to define an inner node called 'override'. (summarymap.def does)
    // The generated inner 'Override' class would otherwise be mistaken for the annotation.
    """
      |@java.lang.Override
      |public final boolean dispatchGetConfig(ConfigInstance.Producer producer) {
      |  if (producer instanceof Producer) {
      |    ((Producer)producer).getConfig(this);
      |    return true;
      |  }
      |  return false;
      |}
      |
      |@java.lang.Override
      |public final String getDefMd5()       { return CONFIG_DEF_MD5; }
      |@java.lang.Override
      |public final String getDefName()      { return CONFIG_DEF_NAME; }
      |@java.lang.Override
      |public final String getDefNamespace() { return CONFIG_DEF_NAMESPACE; }
    """.stripMargin.trim
  }

  private def getUninitializedScalars(node: InnerCNode): String = {
    val scalarsWithoutDefault = {
      node.getChildren.collect {
        case leaf: LeafCNode if (!leaf.isArray && !leaf.isMap && leaf.getDefaultValue == null) =>
          "\"" + leaf.getName + "\""
      }
    }

    val uninitializedList =
      if (scalarsWithoutDefault.size > 0)
        "Arrays.asList(\n" + indentCode(Indentation, scalarsWithoutDefault.mkString("",",\n","\n)"))
      else
        ""

    "private Set<String> " + InternalPrefix + "uninitialized = new HashSet<String>(" + uninitializedList + ");"
  }

  private def getBuilderFieldDefinition(node: CNode): String = {

    (node match {
      case array if node.isArray =>
        "public List<%s> %s = new ArrayList<>()".format(builderType(array), array.getName)
      case map if node.isMap =>
        "public Map<String, %s> %s = new LinkedHashMap<>()".format(builderType(map), map.getName)
      case struct: InnerCNode =>
        "public %s %s = new %s()".format(builderType(struct), struct.getName, builderType(struct))
      case scalar : LeafCNode =>
        "private " + boxedBuilderType(scalar) + " " + scalar.getName + " = null"
    }) + ";"
  }

  private def getBuilderSetters(node: CNode): String = {
    val children: Array[CNode] = node.getChildren

    def structSetter(node: InnerCNode) = {
      <code>
        |public Builder {node.getName}({builderType(node)} {InternalPrefix}builder) {{
        |  {node.getName} = {InternalPrefix}builder;
        |  return this;
        |}}
      </code>.text.stripMargin.trim
    }

    def innerArraySetters(node: InnerCNode) = {
      <code>
        |/**
        | * Add the given builder to this builder's list of {nodeClass(node)} builders
        | * @param {InternalPrefix}builder a builder
        | * @return this builder
        | */
        |public Builder {node.getName}({builderType(node)} {InternalPrefix}builder) {{
        |  {node.getName}.add({InternalPrefix}builder);
        |  return this;
        |}}
        |
        |/**
        | * Set the given list as this builder's list of {nodeClass(node)} builders
        | * @param __builders a list of builders
        | * @return this builder
        | */
        |public Builder {node.getName}(List&lt;{builderType(node)}&gt; __builders) {{
        |  {node.getName} = __builders;
        |  return this;
        |}}
      </code>.text.stripMargin.trim
    }

    def leafArraySetters(node: LeafCNode) = {
      val setters =
      <code>
        |public Builder {node.getName}({builderType(node)} {InternalPrefix}value) {{
        |  {node.getName}.add({InternalPrefix}value);
        |  return this;
        |}}
        |
        |public Builder {node.getName}(Collection&lt;{builderType(node)}&gt; {InternalPrefix}values) {{
        |  {node.getName}.addAll({InternalPrefix}values);
        |  return this;
        |}}
      </code>.text.stripMargin.trim

      val privateSetter =
        if (builderType(node) == "String" || builderType(node) == "FileReference")
          ""
        else
          "\n\n" +
          <code>
          |
          |
          |private Builder {node.getName}(String {InternalPrefix}value) {{
          |  return {node.getName}({builderType(node)}.valueOf({InternalPrefix}value));
          |}}
          </code>.text.stripMargin.trim

       setters + privateSetter
    }

    def innerMapSetters(node: CNode) = {
      <code>
        |public Builder {node.getName}(String {InternalPrefix}key, {builderType(node)} {InternalPrefix}value) {{
        |  {node.getName}.put({InternalPrefix}key, {InternalPrefix}value);
        |  return this;
        |}}
        |
        |public Builder {node.getName}(Map&lt;String, {builderType(node)}&gt; {InternalPrefix}values) {{
        |  {node.getName}.putAll({InternalPrefix}values);
        |  return this;
        |}}
      </code>.text.stripMargin.trim
    }

    def leafMapSetters(node: LeafCNode) = {
      val privateSetter =
        if (builderType(node) == "String" || builderType(node) == "FileReference")
          ""
        else
          "\n\n" +
          <code>
          |
          |
          |private Builder {node.getName}(String {InternalPrefix}key, String {InternalPrefix}value) {{
          |  return {node.getName}({InternalPrefix}key, {builderType(node)}.valueOf({InternalPrefix}value));
          |}}
          </code>.text.stripMargin.trim

       innerMapSetters(node) + privateSetter
    }

    def scalarSetters(node: LeafCNode): String = {
      val name = node.getName

      val signalInitialized =
        if (node.getDefaultValue == null) InternalPrefix + "uninitialized.remove(\"" + name + "\");\n"
        else ""

      val stringSetter =
        builderType(node) match {
          case "String"        => ""
          case "FileReference" => ""
          case _ =>
            """|
              |private Builder %s(String %svalue) {
              |  return %s(%s.valueOf(%svalue));
              |}""".stripMargin.format(name, InternalPrefix,
              name, boxedDataType(node), InternalPrefix)
        }

      def getNullGuard = {
        if (builderType(node) != boxedBuilderType(node))
          ""
        else
          "\n" + "if (%svalue == null) throw new IllegalArgumentException(\"Null value is not allowed.\");"
            .format(InternalPrefix)
      }

      // TODO: check if 2.9.2 allows string to start with a newline
      """|public Builder %s(%s %svalue) {%s
         |  %s = %svalue;
         |  %s
      """.stripMargin.format(name, builderType(node), InternalPrefix, getNullGuard,
        name, InternalPrefix,
        signalInitialized).trim +
        "\n  return this;" + "\n}\n" +
        stringSetter
    }

    (children collect {
      case innerArray: InnerCNode if innerArray.isArray => innerArraySetters(innerArray)
      case innerMap: InnerCNode if innerMap.isMap       => innerMapSetters(innerMap)
      case leafArray: LeafCNode if leafArray.isArray    => leafArraySetters(leafArray)
      case leafMap: LeafCNode if leafMap.isMap          => leafMapSetters(leafMap)
      case struct: InnerCNode => structSetter(struct)
      case scalar: LeafCNode  => scalarSetters(scalar)
    } ).mkString("\n\n")
  }

  private def getBuilderConstructors(node: CNode, className: String): String = {
    def setBuilderValueFromConfig(child: CNode) = {
      val name = child.getName
      val isArray = child.isArray
      val isMap = child.isMap

      child match {
        case fileArray: FileLeaf if isArray => name + "(" + userDataType(fileArray) + ".toValues(config." + name + "()));"
        case fileMap: FileLeaf if isMap => name + "(" + userDataType(fileMap) + ".toValueMap(config." + name + "()));"
        case file: FileLeaf => name + "(config." + name + "().value());"
        case pathArray: PathLeaf if isArray => name + "(" + nodeClass(pathArray) + ".toFileReferences(config." + name + "));"
        case pathMap: PathLeaf if isMap => name + "(" + nodeClass(pathMap) + ".toFileReferenceMap(config." + name + "));"
        case path: PathLeaf => name + "(config." + name + ".getFileReference());"
        case leaf: LeafCNode => name + "(config." + name + "());"
        case innerArray: InnerCNode if isArray => setInnerArrayBuildersFromConfig(innerArray)
        case innerMap: InnerCNode   if isMap   => setInnerMapBuildersFromConfig(innerMap)
        case struct => name + "(new " +  builderType(struct) + "(config." + name + "()));"
      }
    }

    def setInnerArrayBuildersFromConfig(innerArr: InnerCNode) = {
      val elemName = createUniqueSymbol(node, innerArr.getName)
      <code>
        |for ({userDataType(innerArr)} {elemName} : config.{innerArr.getName}()) {{
        |  {innerArr.getName}(new {builderType(innerArr)}({elemName}));
        |}}
      </code>.text.stripMargin.trim
    }

    def setInnerMapBuildersFromConfig(innerMap: InnerCNode) = {
      val entryName = InternalPrefix + "entry"
      <code>
        |for (Map.Entry&lt;String, {userDataType(innerMap)}&gt; {entryName} : config.{innerMap.getName}().entrySet()) {{
        |  {innerMap.getName}({entryName}.getKey(), new {userDataType(innerMap)}.Builder({entryName}.getValue()));
        |}}
      </code>.text.stripMargin.trim
    }

    <code>
      |public Builder() {{ }}
      |
      |public Builder({className} config) {{
      |{indentCode(Indentation, node.getChildren.map(setBuilderValueFromConfig).mkString("\n"))}
      |}}
    </code>.text.stripMargin.trim
  }

  def arrayOverride(name: String, superior: String): String = {
    Indentation + name + ".addAll(" + superior + "." + name + ");"
  }

  private def getOverrideMethod(node:CNode): String =  {
    val method = "override"
    val superior = InternalPrefix + "superior"

    def callSetter(name: String): String = {
      name + "(" + superior + "." + name + ");"
    }
    def overrideBuilderValue(child: CNode) = {
      val name = child.getName
      child match {
        case leafArray: CNode if (child.isArray) =>
          conditionStatement(child) + "\n" + arrayOverride(name, superior)
        case struct: InnerCNode if !(child.isArray || child.isMap) =>
          name + "(" + name + "." + method + "(" + superior + "." + name + "));"
        case map: CNode if child.isMap =>
          callSetter(name)
        case _ =>
          conditionStatement(child) + "\n" +
            Indentation + callSetter(name)
      }
    }

    def conditionStatement(child: CNode) = {
      val name = child.getName
      val isArray = child.isArray
      val isMap = child.isMap
      child match {
        case _ if isArray => "if (!" + superior + "." + name + ".isEmpty())"
        case _ if isMap => ""
        case scalar: LeafCNode => "if (" + superior + "." + name + " != null)"
        case struct => ""
      }
    }

    <code>
      |private Builder {method}(Builder {superior}) {{
      |{indentCode(Indentation, node.getChildren.map(overrideBuilderValue).mkString("\n"))}
      |  return this;
      |}}
    </code>.text.stripMargin.trim
  }

  private def builderType(node: CNode): String = {
    node match {
      case inner: InnerCNode => boxedDataType(node) + ".Builder"
      case file: FileLeaf => "String"
      case path: PathLeaf => "FileReference"
      case leafArray: LeafCNode if (node.isArray || node.isMap) => boxedDataType(node)
      case _ => userDataType(node)
    }
  }

  private def boxedBuilderType(node: LeafCNode): String = {
    node match {
      case file: FileLeaf => "String"
      case path: PathLeaf => "FileReference"
      case _ => boxedDataType(node)
    }
  }

}
