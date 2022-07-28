// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.Field;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.schema.derived.Deriver;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Einar M R Rosenvinge
 */
public class FieldOfTypeDocumentTestCase extends AbstractSchemaTestCase {

    @Test
    void testDocument() throws IOException {

        List<String> sds = new ArrayList<>();
        sds.add("src/test/examples/music.sd");
        sds.add("src/test/examples/fieldoftypedocument.sd");
        DocumentmanagerConfig.Builder value = Deriver.getDocumentManagerConfig(sds);
        assertConfigFile("src/test/examples/fieldoftypedocument.cfg",
                new DocumentmanagerConfig(value).toString() + "\n");

        DocumentTypeManager manager = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(manager, "raw:" + new DocumentmanagerConfig(value).toString());


        DocumentType musicType = manager.getDocumentType("music");
        assertEquals(3, musicType.getFieldCount());

        Field intField = musicType.getField("intfield");
        assertEquals(DataType.INT, intField.getDataType());
        Field stringField = musicType.getField("stringfield");
        assertEquals(DataType.STRING, stringField.getDataType());
        Field longField = musicType.getField("longfield");
        assertEquals(DataType.LONG, longField.getDataType());


        DocumentType bookType = manager.getDocumentType("book");
        assertEquals(1, bookType.getFieldCount());

        Field musicField = bookType.getField("soundtrack");
        assertSame(musicType, musicField.getDataType());
    }

}

