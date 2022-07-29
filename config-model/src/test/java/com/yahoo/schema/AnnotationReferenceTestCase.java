// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.document.DataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.config.model.deploy.TestProperties;
import org.junit.jupiter.api.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author arnej
 */
public class AnnotationReferenceTestCase {

    static final String sd =
        joinLines("search test {",
                  "    document test { ",
                  "        struct mystruct {",
                  "          field x type int {}",
                  "        }",
                  "        field a type string {}",
                  "        field b type mystruct {}",
                  "        annotation marker {}",
                  "        annotation person {",
                  "            field name type string {}",
                  "            field age type int {}",
                  "        }",
                  "        annotation complex {",
                  "            field title type string {}",
                  "            field tag type annotationreference<marker> {}",
                  "            field owner type annotationreference<person> {}",
                  "        }",
                  "    }",
                  "}");

    @Test
    void noAnnotationReferenceInDocument() throws Exception {
        var builder = new ApplicationBuilder(new TestProperties());
        builder.addSchema(sd);
        builder.build(true);
        var doc = builder.getSchema().getDocument();
        checkForAnnRef(doc);
        var complex = doc.findAnnotation("complex");
        var dt = complex.getDataType();
        assertTrue(dt instanceof StructDataType);
        var struct = (StructDataType) dt;
        var field = struct.getField("owner");
        assertTrue(field.getDataType() instanceof AnnotationReferenceDataType);
    }

    void checkForAnnRef(SDDocumentType doc) {
        for (var child : doc.getTypes()) {
            checkForAnnRef(child);
        }
        for (Field field : doc.fieldSet()) {
            DataType fieldType = field.getDataType();
            assertFalse(fieldType instanceof AnnotationReferenceDataType);
        }
    }
        
}
