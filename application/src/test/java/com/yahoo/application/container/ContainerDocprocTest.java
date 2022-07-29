// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Application;
import com.yahoo.application.ApplicationBuilder;
import com.yahoo.application.Networking;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.application.container.docprocs.Rot13DocumentProcessor;
import com.yahoo.container.Container;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.processing.execution.chain.ChainRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
public class ContainerDocprocTest {

    private static final String DOCUMENT = "document music {\n"
                                           + "  field title type string { }\n"
                                           + "}\n";
    private static final String CHAIN_NAME = "myChain";


    private static String getXML(String chainName, String... processorIds) {
        String xml =
                "<container version=\"1.0\">\n" +
                "  <document-processing>\n" +
                "    <chain id=\"" + chainName + "\">\n";
        for (String processorId : processorIds) {
            xml += "      <documentprocessor id=\"" + processorId + "\"/>\n";
        }
        xml +=
                "    </chain>\n" +
                "  </document-processing>\n" +
                "  <accesslog type=\"disabled\" />" +
                "</container>\n";
        return xml;
    }

    @BeforeEach
    public void resetContainer() {
        Container.resetInstance();
    }

    @Test
    void requireThatBasicDocumentProcessingWorks() throws Exception {
        try (Application app = new ApplicationBuilder()
                .servicesXml(getXML(CHAIN_NAME, Rot13DocumentProcessor.class.getCanonicalName()))
                .documentType("music", DOCUMENT).build()) {

            JDisc container = app.getJDisc("container");
            DocumentProcessing docProc = container.documentProcessing();
            DocumentType type = docProc.getDocumentTypes().get("music");

            ChainRegistry<DocumentProcessor> chains = docProc.getChains();
            assertTrue(chains.allComponentsById().containsKey(new ComponentId(CHAIN_NAME)));

            Document doc = new Document(type, "id:this:music::is:a:great:album");
            doc.setFieldValue("title", "Great Album!");
            com.yahoo.docproc.Processing processing;
            DocumentProcessor.Progress progress;
            DocumentPut put = new DocumentPut(doc);

            processing = com.yahoo.docproc.Processing.of(put);
            progress = docProc.process(ComponentSpecification.fromString(CHAIN_NAME), processing);
            assertSame(progress, DocumentProcessor.Progress.DONE);
            assertEquals("Terng Nyohz!", doc.getFieldValue("title").toString());

            processing = com.yahoo.docproc.Processing.of(put);
            progress = docProc.process(ComponentSpecification.fromString(CHAIN_NAME), processing);
            assertSame(progress, DocumentProcessor.Progress.DONE);
            assertEquals("Great Album!", doc.getFieldValue("title").toString());
        }
    }

    @Test
    void requireThatLaterDocumentProcessingWorks() throws Exception {
        try (Application app = new ApplicationBuilder()
                .servicesXml(getXML(CHAIN_NAME, Rot13DocumentProcessor.class.getCanonicalName()))
                .networking(Networking.disable)
                .documentType("music", DOCUMENT).build()) {
            JDisc container = app.getJDisc("container");
            DocumentProcessing docProc = container.documentProcessing();
            DocumentType type = docProc.getDocumentTypes().get("music");

            ChainRegistry<DocumentProcessor> chains = docProc.getChains();
            assertTrue(chains.allComponentsById().containsKey(new ComponentId(CHAIN_NAME)));

            Document doc = new Document(type, "id:this:music::is:a:great:album");
            doc.setFieldValue("title", "Great Album!");
            com.yahoo.docproc.Processing processing;
            DocumentProcessor.Progress progress;
            DocumentPut put = new DocumentPut(doc);

            processing = com.yahoo.docproc.Processing.of(put);

            progress = docProc.processOnce(ComponentSpecification.fromString(CHAIN_NAME), processing);
            assertTrue(progress instanceof  DocumentProcessor.LaterProgress);
            assertEquals("Great Album!", doc.getFieldValue("title").toString());

            progress = docProc.processOnce(ComponentSpecification.fromString(CHAIN_NAME), processing);
            assertTrue(progress instanceof DocumentProcessor.LaterProgress);
            assertEquals("Great Album!", doc.getFieldValue("title").toString());

            progress = docProc.processOnce(ComponentSpecification.fromString(CHAIN_NAME), processing);
            assertSame(progress, DocumentProcessor.Progress.DONE);
            assertEquals("Terng Nyohz!", doc.getFieldValue("title").toString());
        }
    }

    @Test
    void requireThatUnknownChainThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            try (JDisc container = JDisc.fromServicesXml(
                    getXML("foo", Rot13DocumentProcessor.class.getCanonicalName()),
                    Networking.disable)) {
                container.documentProcessing().process(ComponentSpecification.fromString("unknown"),
                        new com.yahoo.docproc.Processing());
            }

        });

    }


    @Test
    void requireThatProcessingFails() {
        assertThrows(UnsupportedOperationException.class, () -> {
            try (JDisc container = JDisc.fromServicesXml(
                    getXML("foo", Rot13DocumentProcessor.class.getCanonicalName()),
                    Networking.disable)) {
                container.processing();
            }

        });

    }

    @Test
    void requireThatSearchFails() {
        assertThrows(UnsupportedOperationException.class, () -> {
            try (JDisc container = JDisc.fromServicesXml(
                    getXML("foo", Rot13DocumentProcessor.class.getCanonicalName()),
                    Networking.disable)) {
                container.search();
            }

        });

    }

}
