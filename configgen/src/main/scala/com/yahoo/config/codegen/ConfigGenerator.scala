// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.codegen


import com.yahoo.config.codegen.BuilderGenerator.getBuilder
import com.yahoo.config.codegen.JavaClassBuilder.Indentation
import com.yahoo.config.codegen.LeafCNode._
import com.yahoo.config.codegen.ReservedWords.{INTERNAL_PREFIX => InternalPrefix}

/**
 * @author gjoranv
 * @author tonytv
 */
// TODO: don't take indent as method param - the caller should indent
object ConfigGenerator {

  def generateContent(indent: String, node: InnerCNode, isOuter: Boolean = true): String = {
    val children: Array[CNode] = node.getChildren

    def generateCodeForChildren: String = {
      (children collect {
        case enum:  EnumLeaf    => getEnumCode(enum, "") + "\n"
        case inner: InnerCNode  => getInnerDefinition(inner, indent) + "\n"
      } ).mkString("\n")
    }

    def getInnerDefinition(inner: InnerCNode,  indent: String) = {
      <code>
        |{getClassDoc(inner, indent)}
        |{getClassDeclaration(inner)}
        |{generateContent(indent, inner, false)}
      </code>.text.stripMargin.trim + "\n}"
    }

    def getClassDeclaration(node: CNode): String = {
      "public final static class " + nodeClass(node)+ " extends InnerNode { " + "\n"
    }

    def getFieldDefinition(node: CNode): String = {
      node.getCommentBlock("//") + "private final " +
        (node match {
          case _: LeafCNode if node.isArray =>
            "LeafNodeVector<%s, %s> %s;".format(boxedDataType(node), nodeClass(node), node.getName)
          case _: InnerCNode if node.isArray =>
            "InnerNodeVector<%s> %s;".format(nodeClass(node), node.getName)
          case _ if node.isMap =>
            "Map<String, %s> %s;".format(nodeClass(node), node.getName)
          case _ =>
            "%s %s;".format(nodeClass(node), node.getName)
        })
    }

    def getStaticMethods = {
      if (node.isArray) getStaticMethodsForInnerArray(node) + "\n\n"
      else if (node.isMap) getStaticMethodsForInnerMap(node) + "\n\n"
      else ""
    }

    def getContainsFieldsFlaggedWithRestart(node: CNode): String = {
      if (isOuter) {
        """
          |private static boolean containsFieldsFlaggedWithRestart() {
          |  return %b;
          |}
        """.stripMargin.trim.format(node.needRestart) + "\n\n"
      } else ""
    }

    indentCode(indent,
      getBuilder(node) + "\n\n" +
        children.map(getFieldDefinition).mkString("\n") + "\n\n" +
        getConstructors(node) + "\n\n" +
        getAccessors(children) + "\n\n" +
        getGetChangesRequiringRestart(node) + "\n\n" +
        getContainsFieldsFlaggedWithRestart(node) +
        getStaticMethods +
        generateCodeForChildren
    )
  }

  private def getGetChangesRequiringRestart(node: InnerCNode): String = {
    def quotedComment(node: CNode): String = {
      node.getComment.replace("\n", "\\n").replace("\"", "\\\"")
    }

    def getComparison(node: CNode): String = node match {
      case inner: InnerCNode if inner.isArray =>
        <code>
          |  changes.compareArray(this.{inner.getName}, newConfig.{inner.getName}, "{inner.getName}", "{quotedComment(inner)}",
          |                      (a,b) -> (({nodeClass(inner)})a).getChangesRequiringRestart(({nodeClass(inner)})b));
        </code>.text.stripMargin.trim
      case inner: InnerCNode if inner.isMap =>
        <code>
          |  changes.compareMap(this.{inner.getName}, newConfig.{inner.getName}, "{inner.getName}", "{quotedComment(inner)}",
          |                    (a,b) -> (({nodeClass(inner)})a).getChangesRequiringRestart(({nodeClass(inner)})b));
        </code>.text.stripMargin.trim
      case inner: InnerCNode =>
        <code>
          |  changes.mergeChanges("{inner.getName}", this.{inner.getName}.getChangesRequiringRestart(newConfig.{inner.getName}));
        </code>.text.stripMargin.trim
      case node: CNode if node.isArray =>
        <code>
          |  changes.compareArray(this.{node.getName}, newConfig.{node.getName}, "{node.getName}", "{quotedComment(node)}",
          |                      (a,b) -> new ChangesRequiringRestart("{node.getName}").compare(a,b,"","{quotedComment(node)}"));
        </code>.text.stripMargin.trim
      case node: CNode if node.isMap =>
        <code>
          |  changes.compareMap(this.{node.getName}, newConfig.{node.getName}, "{node.getName}", "{quotedComment(node)}",
          |                    (a,b) -> new ChangesRequiringRestart("{node.getName}").compare(a,b,"","{quotedComment(node)}"));
        </code>.text.stripMargin.trim
      case node: CNode =>
        <code>
          |  changes.compare(this.{node.getName}, newConfig.{node.getName}, "{node.getName}", "{quotedComment(node)}");
        </code>.text.stripMargin.trim
    }

    val comparisons =
      for {
        c <- node.getChildren if c.needRestart
      } yield "\n  " + getComparison(c)

    <code>
      |private ChangesRequiringRestart getChangesRequiringRestart({nodeClass(node)} newConfig) {{
      |  ChangesRequiringRestart changes = new ChangesRequiringRestart("{node.getName}");{comparisons.mkString("")}
      |  return changes;
      |}}
    </code>.text.stripMargin.trim
  }


  private def scalarDefault(scalar: LeafCNode): String = {
    scalar match {
      case _ if scalar.getDefaultValue == null => ""
      case enumWithNullDefault: EnumLeaf if enumWithNullDefault.getDefaultValue.getValue == null => ""
      case enum: EnumLeaf => nodeClass(enum) + "." + enum.getDefaultValue.getStringRepresentation
      case long: LongLeaf => long.getDefaultValue.getStringRepresentation + "L"
      case double: DoubleLeaf => double.getDefaultValue.getStringRepresentation + "D"
      case _ => scalar.getDefaultValue.getStringRepresentation
    }
  }

  private def getConstructors(inner: InnerCNode) = {

    def assignFromBuilder(child: CNode) = {
      val name = child.getName
      val className = nodeClass(child)
      val dataType = boxedDataType(child)
      val isArray = child.isArray
      val isMap = child.isMap

      def assignIfInitialized(leaf: LeafCNode) = {
        <code>
          |{name} = (builder.{name} == null) ?
          |    new {className}({scalarDefault(leaf)}) : new {className}(builder.{name});
        </code>.text.stripMargin.trim
      }

      child match {
        case fileArray: FileLeaf if isArray =>
          name + " = LeafNodeVector.createFileNodeVector(builder."+ name +");"
        case pathArray: PathLeaf if isArray =>
          name + " = LeafNodeVector.createPathNodeVector(builder."+ name +");"
        case leafArray: LeafCNode if isArray =>
          name + " = new LeafNodeVector<>(builder."+ name +", new " + className + "());"
        case fileMap: LeafCNode if isMap && child.isInstanceOf[FileLeaf] =>
          name + " = LeafNodeMaps.asFileNodeMap(builder."+ name +");"
        case pathMap: LeafCNode if isMap && child.isInstanceOf[PathLeaf] =>
          name + " = LeafNodeMaps.asPathNodeMap(builder."+ name +");"
        case leafMap: LeafCNode if isMap =>
          name + " = LeafNodeMaps.asNodeMap(builder."+ name +", new " + className + "());"
        case innerArray: InnerCNode if isArray =>
          name + " = " + className + ".createVector(builder." + name + ");"
        case innerMap: InnerCNode if isMap =>
          name + " = " + className + ".createMap(builder." + name + ");"
        case struct: InnerCNode =>
          name + " = new " +  className + "(builder." + name + ", throwIfUninitialized);"
        case leaf: LeafCNode =>
          assignIfInitialized(leaf)
      }
    }

    // TODO: merge these two constructors into one when the config library uses builders to set values from payload.
    <code>
      |public {nodeClass(inner)}(Builder builder) {{
      |  this(builder, true);
      |}}
      |
      |private {nodeClass(inner)}(Builder builder, boolean throwIfUninitialized) {{
      |  if (throwIfUninitialized &amp;&amp; ! builder.{InternalPrefix}uninitialized.isEmpty())
      |    throw new IllegalArgumentException("The following builder parameters for " +
      |        "{inner.getFullName} must be initialized: " + builder.{InternalPrefix}uninitialized);
      |
      |{indentCode(Indentation, inner.getChildren.map(assignFromBuilder).mkString("\n"))}
      |}}
    </code>.text.stripMargin.trim
  }

  private def getAccessors(children: Array[CNode]): String = {

    def getAccessorCode(indent: String, node: CNode): String = {
      indentCode(indent,
        if (node.isArray)
          accessorsForArray(node)
        else if (node.isMap)
          accessorsForMap(node)
        else
          accessorForStructOrScalar(node))
    }

    def valueAccessor(node: CNode) = node match {
      case leaf: LeafCNode => ".value()"
      case inner           => ""
    }

    def listAccessor(node: CNode) = node match {
      case leaf: LeafCNode => "%s.asList()".format(leaf.getName)
      case inner           => inner.getName
    }

    def mapAccessor(node: CNode) = node match {
       case leaf: LeafCNode => "LeafNodeMaps.asValueMap(%s)".format(leaf.getName)
       case inner           => "Collections.unmodifiableMap(%s)".format(inner.getName)
     }

    def accessorsForArray(node: CNode): String = {
      val name = node.getName
      val fullName = node.getFullName
      <code>
        |/**
        | * @return {fullName}
        | */
        |public List&lt;{boxedDataType(node)}&gt; {name}() {{
        |  return {listAccessor(node)};
        |}}
        |
        |/**
        | * @param i the index of the value to return
        | * @return {fullName}
        | */
        |public {userDataType(node)} {name}(int i) {{
        |  return {name}.get(i){valueAccessor(node)};
        |}}
      </code>.text.stripMargin.trim
    }

    def accessorsForMap(node: CNode): String = {
      val name = node.getName
      val fullName = node.getFullName
      <code>
        |/**
        | * @return {fullName}
        | */
        |public Map&lt;String, {boxedDataType(node)}&gt; {name}() {{
        |  return {mapAccessor(node)};
        |}}
        |
        |/**
        | * @param key the key of the value to return
        | * @return {fullName}
        | */
        |public {userDataType(node)} {name}(String key) {{
        |  return {name}.get(key){valueAccessor(node)};
        |}}
      </code>.text.stripMargin.trim
    }

    def accessorForStructOrScalar(node: CNode): String = {
      <code>
        |/**
        | * @return {node.getFullName}
        | */
        |public {userDataType(node)} {node.getName}() {{
        |  return {node.getName}{valueAccessor(node)};
        |}}
      </code>.text.stripMargin.trim
    }

    val accessors =
      for {
        c <- children
        accessor = getAccessorCode("", c)
        if (accessor.length > 0)
      } yield (accessor + "\n")
    accessors.mkString("\n").trim
  }

  private def getStaticMethodsForInnerArray(inner: InnerCNode) = {
    """
      |private static InnerNodeVector<%s> createVector(List<Builder> builders) {
      |    List<%s> elems = new ArrayList<>();
      |    for (Builder b : builders) {
      |        elems.add(new %s(b));
      |    }
      |    return new InnerNodeVector<%s>(elems);
      |}
    """.stripMargin.format(List.fill(5)(nodeClass(inner)): _*).trim
  }

  private def getStaticMethodsForInnerMap(inner: InnerCNode) = {
    """
      |private static Map<String, %s> createMap(Map<String, Builder> builders) {
      |  Map<String, %s> ret = new LinkedHashMap<>();
      |  for(String key : builders.keySet()) {
      |    ret.put(key, new %s(builders.get(key)));
      |  }
      |  return Collections.unmodifiableMap(ret);
      |}
    """.stripMargin.format(List.fill(3)(nodeClass(inner)): _*).trim
  }

  private def getEnumCode(enum: EnumLeaf, indent: String): String = {

    def getEnumValues(enum: EnumLeaf): String = {
      val enumValues =
        for (value <- enum.getLegalValues) yield
          """  public final static Enum %s = Enum.%s;""".format(value, value)
      enumValues.mkString("\n")
    }

    // TODO: try to rewrite to xml
    val code =
      """
        |%s
        |public final static class %s extends EnumNode<%s> {

        |  public %s(){
        |    this.value = null;
        |  }

        |  public %s(Enum enumValue) {
        |    super(enumValue != null);
        |    this.value = enumValue;
        |  }

        |  public enum Enum {%s}
        |%s

        |  @Override
        |  protected boolean doSetValue(@NonNull String name) {
        |    try {
        |      value = Enum.valueOf(name);
        |      return true;
        |    } catch (IllegalArgumentException e) {
        |    }
        |    return false;
        |  }
        |}
        |"""
        .stripMargin.format(getClassDoc(enum, indent),
                            nodeClass(enum),
                            nodeClass(enum)+".Enum",
                            nodeClass(enum),
                            nodeClass(enum),
                            enum.getLegalValues.mkString(", "),
                            getEnumValues(enum))

    indentCode(indent, code).trim
  }

  def getClassDoc(node: CNode, indent: String): String = {
    val header = "/**\n" + " * This class represents " + node.getFullName
    val nodeComment = node.getCommentBlock(" *") match {
      case "" => ""
      case s => "\n *\n" + s.stripLineEnd   // TODO: strip trailing \n in CNode.getCommentBlock
    }
    header + nodeComment + "\n */"
  }

  def indentCode(indent: String, code: String): String = {
    val indentedLines =
      for (s <- code.split("\n", -1)) yield
        if (s.length() > 0) (indent + s) else s
    indentedLines.mkString("\n")
  }

  /**
   * @return the name of the class that is generated by this node.
   */
  def nodeClass(node: CNode): String = {
    node match {
      case emptyName: CNode if node.getName.length == 0 =>
        throw new CodegenRuntimeException("Node with empty name, under parent " + emptyName.getParent.getName)
      case root: InnerCNode if root.getParent == null => ConfiggenUtil.createClassName(root.getName)
      case b: BooleanLeaf   => "BooleanNode"
      case d: DoubleLeaf    => "DoubleNode"
      case f: FileLeaf      => "FileNode"
      case p: PathLeaf      => "PathNode"
      case i: IntegerLeaf   => "IntegerNode"
      case l: LongLeaf      => "LongNode"
      case r: ReferenceLeaf => "ReferenceNode"
      case s: StringLeaf    => "StringNode"
      case _ => node.getName.capitalize
    }
  }

  def userDataType(node: CNode): String = {
    node match {
      case inner: InnerCNode => nodeClass(node)
      case enum: EnumLeaf    => nodeClass(enum) + ".Enum"
      case b: BooleanLeaf    => "boolean"
      case d: DoubleLeaf     => "double"
      case f: FileLeaf       => "FileReference"
      case p: PathLeaf       => "Path"
      case i: IntegerLeaf    => "int"
      case l: LongLeaf       => "long"
      case s: StringLeaf     => "String"
    }
  }

  /**
   * @return the boxed java data type, e.g. Integer for int
   */
  def boxedDataType(node: CNode): String = {
    val rawType = userDataType(node)

    rawType match {
      case "int" => "Integer"
      case _ if rawType == rawType.toLowerCase => rawType.capitalize
      case _ => rawType
    }
  }

}
