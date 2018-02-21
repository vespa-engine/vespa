// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Networking;
import com.yahoo.application.container.processors.Rot13Processor;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.Container;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;

/**
 * @author Einar M R Rosenvinge
 */
public class JDiscContainerProcessingTest {

    private static String getXML(String chainName, String... processorIds) {
        String xml =
                "<container version=\"1.0\">\n" +
                "  <processing>\n" +
                "    <chain id=\"" + chainName + "\">\n";
        for (String processorId : processorIds) {
            xml += "      <processor id=\"" + processorId + "\"/>\n";
        }
        xml +=
                "    </chain>\n" +
                "  </processing>\n" +
                "</container>\n";
        return xml;
    }

    @Before
    public void resetContainer() {
        Container.resetInstance();
    }

    @Test
    public void requireThatBasicProcessingWorks() {
        try (JDisc container = getContainerWithRot13()) {
            Processing processing = container.processing();

            Request req = new Request();
            req.properties().set("title", "Good day!");
            Response response = processing.process(ComponentSpecification.fromString("foo"), req);

            assertThat(response.data().get(0).toString(), equalTo("Tbbq qnl!"));
        }
    }

    @Test
    public void requireThatBasicProcessingDoesNotTruncateBigResponse() {
        final int SIZE = 50*1000;
        StringBuilder foo = new StringBuilder();
        for (int j = 0 ; j < SIZE ; j++) {
            foo.append('b');
        }

        try (JDisc container = getContainerWithRot13()) {
            final int NUM_TIMES = 100;
            for (int i = 0; i < NUM_TIMES; i++) {


                com.yahoo.application.container.handler.Response response =
                        container.handleRequest(
                                new com.yahoo.application.container.handler.Request("http://foo/processing/?chain=foo&title=" + foo.toString()));

                assertThat(response.getBody().length, is(SIZE+26));
            }
        }
    }

    @Test
    public void processing_and_rendering_works() throws Exception {
        try (JDisc container = getContainerWithRot13()) {
            Processing processing = container.processing();

            Request req = new Request();
            req.properties().set("title", "Good day!");

            byte[] rendered = processing.processAndRender(ComponentSpecification.fromString("foo"),
                    ComponentSpecification.fromString("default"), req);
            String renderedAsString = new String(rendered, "utf-8");

            assertThat(renderedAsString, containsString("Tbbq qnl!"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void requireThatUnknownChainThrows() {
        try (JDisc container = getContainerWithRot13()) {
            container.processing().process(ComponentSpecification.fromString("unknown"), new Request());
        }

    }

    @Test(expected = UnsupportedOperationException.class)
    public void requireThatDocprocFails() {
        try (JDisc container = getContainerWithRot13()) {
            container.documentProcessing();
        }

    }

    @Test(expected = UnsupportedOperationException.class)
    public void requireThatSearchFails() {
        try (JDisc container = getContainerWithRot13()) {
            container.search();
        }

    }

    private JDisc getContainerWithRot13() {
        return JDisc.fromServicesXml(
                getXML("foo", Rot13Processor.class.getCanonicalName()),
                Networking.disable);
    }

}
