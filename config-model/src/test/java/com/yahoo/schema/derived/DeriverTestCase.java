// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.schema.AbstractSchemaTestCase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests deriving using the Deriver facade
 *
 * @author bratseth
 */
public class DeriverTestCase extends AbstractSchemaTestCase {

    @Test
    void testDeriveDocManager() {
        DocumentTypeManager dtm = new DocumentTypeManager(new DocumentmanagerConfig(
                Deriver.getDocumentManagerConfig(List.of(
                        "src/test/derived/deriver/child.sd",
                        "src/test/derived/deriver/parent.sd",
                        "src/test/derived/deriver/grandparent.sd"))));
        assertEquals(dtm.getDocumentType("child").getField("a").getDataType(), DataType.STRING);
    }

}
