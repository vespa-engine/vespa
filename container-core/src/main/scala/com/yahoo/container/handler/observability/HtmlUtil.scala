// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.observability

import xml.{PrettyPrinter, Elem}


/**
 * @author gjoranv
 * @author tonytv
 */
object HtmlUtil {
  def link(target: String, anchor: String): Elem =
    <a href={target}>{anchor}</a>

  def link(targetAndAnchor: String): Elem = link(targetAndAnchor, targetAndAnchor)

  def unorderedList(items: Elem*) =
    <ul>
      {items}
    </ul>

  def li[T](children: T*) =
    <li>{children}</li>

  def h1(name: String) =
    <h1>{name}</h1>

  def html(title: String, body: Elem*) =
    <html>
      <head>
        <title>{title}</title>
      </head>
      <body>
        {body}
      </body>
    </html>

  def prettyPrintXhtml(elem: Elem): String = {
    """<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">""" +
      "\n" + new PrettyPrinter(120, 2).format(elem)
  }
}
