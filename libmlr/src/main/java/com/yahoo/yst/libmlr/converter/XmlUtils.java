// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yst.libmlr.converter;

import java.util.ArrayList;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XmlUtils {

    public static Element getFirstChildElement(Node parent) {
        if (parent == null)
            return null;

        Node nd = parent.getFirstChild();
        while (nd != null) {
            //System.out.println("type: " + nd.getNodeType() + " name: " + nd.getNodeName());
            if (nd.getNodeType() == Node.ELEMENT_NODE) {
                return (Element)nd;
            }
            nd = nd.getNextSibling();
        }

        return null;
    }

    public static Element getFirstChildElementByName(Node parent, String childName) {
        if (parent == null)
            return null;

        Node nd = parent.getFirstChild();
        while (nd != null) {
            //System.out.println("type: " + nd.getNodeType() + " name: " + nd.getNodeName());
            if (nd.getNodeType() == Node.ELEMENT_NODE
                    && nd.getNodeName().equals(childName)) {
                return (Element)nd;
            }
            nd = nd.getNextSibling();
        }

        return null;
    }

    public static ArrayList<Element> getChildrenByName(Node parent, String childName) {
        if (parent == null)
            return null;

        ArrayList<Element> list = new ArrayList<Element>();
        Node nd = parent.getFirstChild();
        while (nd != null) {
            //System.out.println("type: " + nd.getNodeType() + " name: " + nd.getNodeName());
            if (nd.getNodeType() == Node.ELEMENT_NODE
                    && nd.getNodeName().equals(childName)) {
                list.add((Element)nd);
            }
            nd = nd.getNextSibling();
        }

        if (list.size() == 0)
            return null;
        else
            return list;
    }

}
