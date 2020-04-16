// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.searchdefinition.SchemaTestCase;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests deriving using the Deriver facade
 *
 * @author bratseth
 */
public class DeriverTestCase extends SchemaTestCase {

    @Test
    public void testDeriveDocManager() {
        DocumentTypeManager dtm = new DocumentTypeManager(new DocumentmanagerConfig(
                Deriver.getDocumentManagerConfig(List.of(
                        "src/test/derived/deriver/child.sd",
                        "src/test/derived/deriver/parent.sd",
                        "src/test/derived/deriver/grandparent.sd"))));
        assertEquals(dtm.getDocumentType("child").getField("a").getDataType(), DataType.STRING);
    }

}
