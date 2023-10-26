// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchlib.gbdt;

import org.junit.Test;
import org.w3c.dom.Element;

import java.util.List;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class XmlHelperTestCase {

    @Test
    public void requireThatAttributeTextCanBeRetrieved() throws Exception {
        Element node = XmlHelper.parseXml("<element a1='v1' a2='v2' />");
        assertEquals("v1", XmlHelper.getAttributeText(node, "a1"));
        assertEquals("v2", XmlHelper.getAttributeText(node, "a2"));
    }

    @Test
    public void requireThatMissingAttributeTextThrowsIllegalArgument() throws Exception {
        try {
            XmlHelper.getAttributeText(XmlHelper.parseXml("<element />"), "a1");
            fail();
        } catch (IllegalArgumentException e) {

        }
        try {
            XmlHelper.getAttributeText(XmlHelper.parseXml("<element a1='' />"), "a1");
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatSingleElementCanBeRetrieved() throws Exception {
        String xml = "<parent>" +
                     "    <child id='a' />" +
                     "</parent>";
        Element element = XmlHelper.getSingleElement(XmlHelper.parseXml(xml), null);
        assertNotNull(element);
        assertEquals("a", XmlHelper.getAttributeText(element, "id"));
    }

    @Test
    public void requireThatNamedSingleElementCanBeRetrieved() throws Exception {
        String xml = "<parent>" +
                     "    <bastard id='a' />" +
                     "    <child id='b' />" +
                     "    <bastard id='c' />" +
                     "</parent>";
        Element element = XmlHelper.getSingleElement(XmlHelper.parseXml(xml), "child");
        assertNotNull(element);
        assertEquals("b", XmlHelper.getAttributeText(element, "id"));
    }

    @Test
    public void requireThatMissingSingleElementThrowsIllegalArgument() throws Exception {
        try {
            XmlHelper.getSingleElement(XmlHelper.parseXml("<parent />"), null);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatMissingNamedSingleElementThrowsIllegalArgument() throws Exception {
        String xml = "<parent>" +
                     "    <bastard id='a' />" +
                     "</parent>";
        try {
            XmlHelper.getSingleElement(XmlHelper.parseXml(xml), "child");
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatAmbigousSingleElementThrowsIllegalArgument() throws Exception {
        String xml = "<parent>" +
                     "    <child id='a' />" +
                     "    <child id='b' />" +
                     "</parent>";
        try {
            XmlHelper.getSingleElement(XmlHelper.parseXml(xml), null);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatAmbigousNamedSingleElementThrowsIllegalArgument() throws Exception {
        String xml = "<parent>" +
                     "    <child id='a' />" +
                     "    <bastard id='b' />" +
                     "    <child id='c' />" +
                     "</parent>";
        try {
            XmlHelper.getSingleElement(XmlHelper.parseXml(xml), "child");
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatChildElementsCanBeRetrieved() throws Exception {
        String xml = "<parent>" +
                     "    <child id='a' />" +
                     "    <child id='b' />" +
                     "</parent>";
        List<Element> lst = XmlHelper.getChildElements(XmlHelper.parseXml(xml), null);
        assertNotNull(lst);
        assertEquals(2, lst.size());
        assertEquals("a", XmlHelper.getAttributeText(lst.get(0), "id"));
        assertEquals("b", XmlHelper.getAttributeText(lst.get(1), "id"));
    }

    @Test
    public void requireThatNamedChildElementsCanBeRetrieved() throws Exception {
        String xml = "<parent>" +
                     "    <child id='a' />" +
                     "    <bastard id='b' />" +
                     "    <child id='c' />" +
                     "</parent>";
        List<Element> lst = XmlHelper.getChildElements(XmlHelper.parseXml(xml), "child");
        assertNotNull(lst);
        assertEquals(2, lst.size());
        assertEquals("a", XmlHelper.getAttributeText(lst.get(0), "id"));
        assertEquals("c", XmlHelper.getAttributeText(lst.get(1), "id"));
    }

    @Test
    public void requireThatChildElementsAreNeverNull() throws Exception {
        List<Element> lst = XmlHelper.getChildElements(XmlHelper.parseXml("<parent />"), null);
        assertNotNull(lst);
        assertTrue(lst.isEmpty());
    }

    @Test
    public void requireThatNamedChildElementsAreNeverNull() throws Exception {
        List<Element> lst = XmlHelper.getChildElements(XmlHelper.parseXml("<parent />"), "child");
        assertNotNull(lst);
        assertTrue(lst.isEmpty());
    }
}
