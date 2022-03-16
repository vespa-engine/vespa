// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.DataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.Field;
import com.yahoo.document.annotation.AnnotationReferenceDataType;
import com.yahoo.searchdefinition.Schema;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.config.model.deploy.TestProperties;
import org.junit.Test;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
    public void noAnnotationReferenceInDocument() throws Exception {
        var builder = new ApplicationBuilder(new TestProperties().setExperimentalSdParsing(true));
        builder.addSchema(sd);
        builder.build(true);
        var doc = builder.getSchema().getDocument();
        checkForAnnRef(doc);
        var complex = doc.findAnnotation("complex");
        System.err.println("annotation: "+complex);
        var dt = complex.getDataType();
        System.err.println("associated datatype: "+dt);
        assertTrue(dt instanceof StructDataType);
        var struct = (StructDataType)dt;
        var field = struct.getField("owner");
        System.err.println("owner field: "+field);
        assertTrue(field.getDataType() instanceof AnnotationReferenceDataType);
    }

    void checkForAnnRef(SDDocumentType doc) {
        for (var child : doc.getTypes()) {
            System.err.println("Check child ["+child+"] of parent "+doc);
            checkForAnnRef(child);
        }
        for (Field field : doc.fieldSet()) {
            DataType fieldType = field.getDataType();
            System.err.println("datatype "+fieldType+" in field  "+field+" in doc "+doc);
            assertFalse(fieldType instanceof AnnotationReferenceDataType);
        }
    }
        
}
