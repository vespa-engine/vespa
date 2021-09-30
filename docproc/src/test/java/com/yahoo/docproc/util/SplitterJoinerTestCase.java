// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.util;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.docproc.SplitterJoinerDocumentProcessorConfig;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Einar M R Rosenvinge
 */
@SuppressWarnings({"unchecked"})
public class SplitterJoinerTestCase {

    @Test
    public void testSplitJoin() {
        ConfigGetter<SplitterJoinerDocumentProcessorConfig> getter = new ConfigGetter<>(SplitterJoinerDocumentProcessorConfig.class);
        ConfigGetter<DocumentmanagerConfig> docManGetter = new ConfigGetter<>(DocumentmanagerConfig.class);

        SplitterJoinerDocumentProcessorConfig cfg =
                getter.getConfig("file:src/test/java/com/yahoo/docproc/util/splitter-joiner-document-processor.cfg");
        DocumentmanagerConfig docManCfg =
                docManGetter.getConfig("file:src/test/java/com/yahoo/docproc/util/documentmanager.docindoc.cfg");

        SplitterDocumentProcessor splitter = new SplitterDocumentProcessor(cfg, docManCfg);

        DocumentTypeManager manager = splitter.manager;


        // Create documents

        Document inner1 = new Document(manager.getDocumentType("docindoc"), "id:inner:docindoc::one");
        inner1.setFieldValue("name", new StringFieldValue("Donald Duck"));
        inner1.setFieldValue("content", new StringFieldValue("Lives in Duckburg"));
        Document inner2 = new Document(manager.getDocumentType("docindoc"), "id:inner:docindoc::number:two");
        inner2.setFieldValue("name", new StringFieldValue("Uncle Scrooge"));
        inner2.setFieldValue("content", new StringFieldValue("Lives in Duckburg, too."));

        Array<Document> innerArray = (Array<Document>) manager.getDocumentType("outerdoc").getField("innerdocuments").getDataType().createFieldValue();
        innerArray.add(inner1);
        innerArray.add(inner2);

        Document outer = new Document(manager.getDocumentType("outerdoc"), "id:outer:outerdoc::the:only:one");
        outer.setFieldValue("innerdocuments", innerArray);

        // End create documents


        Processing p = Processing.of(new DocumentPut(outer));
        splitter.process(p);

        assertEquals(2, p.getDocumentOperations().size());
        assertSame(inner1, ((DocumentPut)(p.getDocumentOperations().get(0))).getDocument());
        assertSame(inner2, ((DocumentPut)(p.getDocumentOperations().get(1))).getDocument());
        assertSame(outer, ((DocumentPut)(p.getVariable(cfg.contextFieldName()))).getDocument());
        assertSame(innerArray, outer.getFieldValue("innerdocuments"));
        assertTrue(innerArray.isEmpty());


        JoinerDocumentProcessor joiner = new JoinerDocumentProcessor(cfg, docManCfg);

        joiner.process(p);

        assertEquals(1, p.getDocumentOperations().size());
        assertSame(outer, ((DocumentPut)p.getDocumentOperations().get(0)).getDocument());
        assertNull(p.getVariable(cfg.contextFieldName()));
        assertSame(innerArray, outer.getFieldValue("innerdocuments"));
        assertEquals(2, innerArray.size());
        assertSame(inner1, innerArray.get(0));
        assertSame(inner2, innerArray.get(1));
    }

}
