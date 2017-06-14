// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.storage.StorMemfilepersistenceConfig;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.engines.VDSEngine;
import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;

import static org.junit.Assert.assertEquals;

public class VDSProviderTest {
    VDSEngine parse(String xml) {
        Document doc = XML.getDocument(xml);
        return new VDSEngine(null, new ModelElement(doc.getDocumentElement()));
    }

    @Test
    public void testTuning() {
        StorMemfilepersistenceConfig.Builder builder = new StorMemfilepersistenceConfig.Builder();

        parse(
                " <vds>\n" +
                "          <tuning>\n" +
                "            <disk-full-ratio>0.93</disk-full-ratio>\n" +
                "            <cache-size>1G</cache-size>\n" +
                "          </tuning>" +
                "</vds>"
        ).getConfig(builder);

        StorMemfilepersistenceConfig config = new StorMemfilepersistenceConfig(builder);

        assertEquals(0.93, config.disk_full_factor(), 0.01);
        assertEquals(1024 * 1024 * 1024, config.cache_size());
    }
}
