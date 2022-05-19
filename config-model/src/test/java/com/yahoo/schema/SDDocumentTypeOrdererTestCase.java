// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.document.DataType;
import com.yahoo.document.DataTypeName;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporarySDDocumentType;
import com.yahoo.schema.document.TemporarySDField;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Einar M R Rosenvinge
 */
public class SDDocumentTypeOrdererTestCase {

    @Test
    public void testOrder() {
        List<SDDocumentType> types = new ArrayList<>();

        SDDocumentType a = new SDDocumentType("a");
        SDDocumentType b = new SDDocumentType("b");
        SDDocumentType c = new SDDocumentType("c");
        SDDocumentType d = new SDDocumentType("d");
        SDDocumentType e = new SDDocumentType("e");
        SDDocumentType f = new SDDocumentType("f");
        SDDocumentType g = new SDDocumentType("g");
        b.inherit(new TemporarySDDocumentType(new DataTypeName("a")));
        c.inherit(new TemporarySDDocumentType(new DataTypeName("b")));
        d.inherit(new TemporarySDDocumentType(new DataTypeName("e")));
        g.inherit(new TemporarySDDocumentType(new DataTypeName("e")));
        g.inherit(new TemporarySDDocumentType(new DataTypeName("c")));

        SDField aFieldTypeB = new TemporarySDField(a, "atypeb", DataType.STRING);
        a.addField(aFieldTypeB);

        SDField bFieldTypeC = new TemporarySDField(b, "btypec", DataType.STRING);
        b.addField(bFieldTypeC);

        SDField cFieldTypeG = new TemporarySDField(c, "ctypeg", DataType.STRING);
        c.addField(cFieldTypeG);

        SDField gFieldTypeF = new TemporarySDField(g, "gtypef", DataType.STRING);
        g.addField(gFieldTypeF);

        SDField fFieldTypeC = new TemporarySDField(f, "ftypec", DataType.STRING);
        f.addField(fFieldTypeC);

        SDField dFieldTypeE = new TemporarySDField(d, "dtypee", DataType.STRING);
        d.addField(dFieldTypeE);

        types.add(a);
        types.add(b);
        types.add(c);
        types.add(d);
        types.add(e);
        types.add(f);
        types.add(g);

        SDDocumentTypeOrderer app = new SDDocumentTypeOrderer(types, new BaseDeployLogger());
        app.process();
        assertEquals(7, app.processingOrder.size());
        assertEquals(a, app.processingOrder.get(0));
        assertEquals(b, app.processingOrder.get(1));
        assertEquals(c, app.processingOrder.get(2));
        assertEquals(e, app.processingOrder.get(3));
        assertEquals(d, app.processingOrder.get(4));
        assertEquals(f, app.processingOrder.get(5));
        assertEquals(g, app.processingOrder.get(6));
    }

}
