// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core

/**
 * Generate dot graph from a ComponentGraph
 *
 * @author tonytv
 * @author gjoranv
 * @since 5.1.4
 */

object DotGraph {

  def generate(graph: ComponentGraph): String = {
    val nodes = graph.nodes map(node)

    val edges = for {
      node <- graph.nodes
      usedNode <- node.usedComponents
    } yield edge(node, usedNode)

    (nodes ++ edges).
        mkString(
      """|digraph {
         |  graph [ratio="compress"];
         |  """.stripMargin, "\n  ", "\n}")
  }

  private def label(node: Node) = {
    node.label.replace("\n", "\\n")
  }

  private def node(node: Node): String = {
    <node>
      "{node.componentId.stringValue()}"[shape=record, fontsize=11, label="{label(node)}"];
    </node>.text.trim
  }

  private def edge(node: Node,  usedNode:Node): String = {
    <edge>
      "{node.componentId.stringValue()}"->"{usedNode.componentId.stringValue()}";
    </edge>.text.trim

  }

}
