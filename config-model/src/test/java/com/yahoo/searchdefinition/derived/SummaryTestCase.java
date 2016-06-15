// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;
import java.util.Iterator;

import static org.junit.Assert.assertEquals;
/**
 * Tests summary extraction
 *
 * @author  <a href="mailto:bratseth@yahoo-inc.com">Jon S Bratseth</a>
 */
public class SummaryTestCase extends SearchDefinitionTestCase {
    @Test
    public void testDeriving() throws IOException, ParseException {
        Search search = SearchBuilder.buildFromFile("src/test/examples/simple.sd");
        SummaryClass summary=new SummaryClass(search,search.getSummary("default"), new BaseDeployLogger());
        assertEquals("default",summary.getName());

        Iterator fields=summary.fieldIterator();

        SummaryClassField field;

        assertEquals(13, summary.getFieldCount());

        field=(SummaryClassField)fields.next();
        assertEquals("exactemento",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("exact",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("title",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("description",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("dyndesc",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("longdesc",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("longstat",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("dynlong",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("dyndesc2",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("measurement",field.getName());
        assertEquals(SummaryClassField.Type.INTEGER,field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("rankfeatures",field.getName());
        assertEquals(SummaryClassField.Type.FEATUREDATA, field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("summaryfeatures",field.getName());
        assertEquals(SummaryClassField.Type.FEATUREDATA, field.getType());

        field=(SummaryClassField)fields.next();
        assertEquals("documentid",field.getName());
        assertEquals(SummaryClassField.Type.LONGSTRING,field.getType());
    }


}
