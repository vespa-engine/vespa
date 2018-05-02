// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.xml.providers;

import org.junit.Test;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLEventFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.29
 */
@SuppressWarnings("deprecation")
public class XMLProviderTest {

    @Test
    public void testInstantiationAndDestruction() {
        {
            DatatypeFactoryProvider provider = new DatatypeFactoryProvider();
            DatatypeFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(DatatypeFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            DocumentBuilderFactoryProvider provider = new DocumentBuilderFactoryProvider();
            DocumentBuilderFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(DocumentBuilderFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            SAXParserFactoryProvider provider = new SAXParserFactoryProvider();
            SAXParserFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(SAXParserFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            SchemaFactoryProvider provider = new SchemaFactoryProvider();
            SchemaFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(SchemaFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            TransformerFactoryProvider provider = new TransformerFactoryProvider();
            TransformerFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(TransformerFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            XMLEventFactoryProvider provider = new XMLEventFactoryProvider();
            XMLEventFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(XMLEventFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            XMLInputFactoryProvider provider = new XMLInputFactoryProvider();
            XMLInputFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(XMLInputFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            XMLOutputFactoryProvider provider = new XMLOutputFactoryProvider();
            XMLOutputFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(XMLOutputFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
        {
            XPathFactoryProvider provider = new XPathFactoryProvider();
            XPathFactory factory = provider.get();
            assertThat(factory.getClass().getName(), equalTo(XPathFactoryProvider.FACTORY_CLASS));
            provider.deconstruct();
        }
    }

}
