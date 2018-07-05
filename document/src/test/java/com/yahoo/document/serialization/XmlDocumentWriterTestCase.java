// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.PredicateFieldValue;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author Simon Thoresen Hult
 */
public class XmlDocumentWriterTestCase {

    @Test
    public void requireThatPredicateFieldValuesAreSerializedAsString() {
        DocumentType docType = new DocumentType("my_type");
        Field field = new Field("my_predicate", DataType.PREDICATE);
        docType.addField(field);
        Document doc = new Document(docType, "doc:scheme:");
        PredicateFieldValue predicate = Mockito.mock(PredicateFieldValue.class);
        doc.setFieldValue("my_predicate", predicate);

        new XmlDocumentWriter().write(doc);
        Mockito.verify(predicate, Mockito.times(1)).printXml(Mockito.any(XmlStream.class));
    }
}
