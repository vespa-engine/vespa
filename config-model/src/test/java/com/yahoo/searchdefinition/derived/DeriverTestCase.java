// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests deriving using the Deriver facade
 *
 * @author bratseth
 */
public class DeriverTestCase extends SearchDefinitionTestCase {

    @Test
    public void testDeriveDocManager() {
        DocumentTypeManager dtm = new DocumentTypeManager(new DocumentmanagerConfig(
                Deriver.getDocumentManagerConfig(new ArrayList<String>() 
                        {{ add("src/test/derived/deriver/child.sd"); 
                           add("src/test/derived/deriver/parent.sd");
                           add("src/test/derived/deriver/grandparent.sd");}})));
        assertEquals(dtm.getDocumentType("child").getField("a").getDataType(), DataType.STRING);
    }

}
