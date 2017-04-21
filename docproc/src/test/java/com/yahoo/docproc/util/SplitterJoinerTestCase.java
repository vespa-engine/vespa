// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
@SuppressWarnings({"unchecked","rawtypes"})
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


        /**** Create documents: ****/

        Document inner1 = new Document(manager.getDocumentType("docindoc"), "doc:inner:number:one");
        inner1.setFieldValue("name", new StringFieldValue("Donald Duck"));
        inner1.setFieldValue("content", new StringFieldValue("Lives in Duckburg"));
        Document inner2 = new Document(manager.getDocumentType("docindoc"), "doc:inner:number:two");
        inner2.setFieldValue("name", new StringFieldValue("Uncle Scrooge"));
        inner2.setFieldValue("content", new StringFieldValue("Lives in Duckburg, too."));

        Array<Document> innerArray = (Array<Document>) manager.getDocumentType("outerdoc").getField("innerdocuments").getDataType().createFieldValue();
        innerArray.add(inner1);
        innerArray.add(inner2);

        Document outer = new Document(manager.getDocumentType("outerdoc"), "doc:outer:the:only:one");
        outer.setFieldValue("innerdocuments", innerArray);

        /**** End create documents ****/


        Processing p = Processing.of(new DocumentPut(outer));
        splitter.process(p);

        assertEquals(2, p.getDocumentOperations().size());
        assertThat(((DocumentPut)(p.getDocumentOperations().get(0))).getDocument(), sameInstance(inner1));
        assertThat(((DocumentPut)(p.getDocumentOperations().get(1))).getDocument(), sameInstance(inner2));
        assertThat(((DocumentPut)(p.getVariable(cfg.contextFieldName()))).getDocument(), sameInstance(outer));
        assertThat(outer.getFieldValue("innerdocuments"), sameInstance(innerArray));
        assertTrue(innerArray.isEmpty());


        JoinerDocumentProcessor joiner = new JoinerDocumentProcessor(cfg, docManCfg);

        joiner.process(p);

        assertThat(p.getDocumentOperations().size(), equalTo(1));
        assertThat(((DocumentPut)p.getDocumentOperations().get(0)).getDocument(), sameInstance(outer));
        assertThat(p.getVariable(cfg.contextFieldName()), nullValue());
        assertThat(outer.getFieldValue("innerdocuments"), sameInstance(innerArray));
        assertThat(innerArray.size(), equalTo(2));
        assertThat(innerArray.get(0), sameInstance(inner1));
        assertThat(innerArray.get(1), sameInstance(inner2));
    }

}
