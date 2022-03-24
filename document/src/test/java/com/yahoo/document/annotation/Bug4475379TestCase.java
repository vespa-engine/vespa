// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class Bug4475379TestCase {

    @Test
    public void testClone() {
        DocumentTypeManager manager = new DocumentTypeManager();
        var sub = DocumentTypeManagerConfigurer.configure
            (manager, "file:src/test/java/com/yahoo/document/annotation/documentmanager.bug4475379.cfg");
        sub.close();

        DocumentType type = manager.getDocumentType("blog");
        Document doc = new Document(type, "id:this:blog::is:a:test");
        doc.setFieldValue("body", new StringFieldValue(""));
        annotate(manager, doc);

        Document anotherDoc = doc.clone();
        assertEquals(doc, anotherDoc);
    }

    public void annotate(DocumentTypeManager manager, Document document) {
        DocumentType docType = manager.getDocumentType("blog");

        AnnotationTypeRegistry registry = manager.getAnnotationTypeRegistry();

        AnnotationType company = registry.getType("company");
        AnnotationType industry = registry.getType("industry");
        AnnotationType person = registry.getType("person");
        AnnotationType location = registry.getType("location");

        Annotation compAn1;
        Annotation personAn1;
        Annotation locAn1;
        Annotation indAn1;
        Annotation compAn2;
        Annotation personAn2;
        Annotation locAn2;
        Annotation indAn2;

        {
            Struct companyValue1 = (Struct) company.getDataType().createFieldValue();
            companyValue1.setFieldValue("name", new StringFieldValue("Sun"));
            companyValue1.setFieldValue("ceo", new StringFieldValue("Scott Mcnealy"));
            companyValue1.setFieldValue("lat", new FloatFieldValue(37.7f));
            companyValue1.setFieldValue("lon", new FloatFieldValue(-122.44f));
            companyValue1.setFieldValue("alt", 60.456);
            companyValue1.setFieldValue("vertical", new StringFieldValue("software"));
            compAn1 = new Annotation(company, companyValue1);
        }
        {
            Struct personValue1 = new Struct(person.getDataType());
            personValue1.setFieldValue("name", new StringFieldValue("Richard Bair"));
            personAn1 = new Annotation(person, personValue1);
        }
        {
            Struct locValue1 = new Struct(location.getDataType());
            locValue1.setFieldValue("name", new StringFieldValue("Prinsens Gate"));
            locAn1 = new Annotation(location, locValue1);
        }
        {
            Struct indValue1 = new Struct(industry.getDataType());
            indValue1.setFieldValue("vertical", new StringFieldValue("Software Services"));
            indAn1 = new Annotation(industry, indValue1);
        }
        {
            Struct companyValue2 = (Struct) company.getDataType().createFieldValue();
            companyValue2.setFieldValue("name", new StringFieldValue("Yahoo"));
            companyValue2.setFieldValue("ceo", new StringFieldValue("Carol Bartz"));
            companyValue2.setFieldValue("lat", new FloatFieldValue(32.1f));
            companyValue2.setFieldValue("lon", new FloatFieldValue(-48.44f));
            companyValue2.setFieldValue("alt", 33.56);
            companyValue2.setFieldValue("vertical", new StringFieldValue("Research"));
            compAn2 = new Annotation(company, companyValue2);
        }
        {
            Struct personValue2 = new Struct(person.getDataType());
            personValue2.setFieldValue("name", new StringFieldValue("Kim Johansen"));
            personAn2 = new Annotation(person, personValue2);
        }
        {
            Struct locValue2 = new Struct(location.getDataType());
            locValue2.setFieldValue("name", new StringFieldValue("RT Nagar"));
            locAn2 = new Annotation(location, locValue2);
        }
        {
            Struct indValue2 = new Struct(industry.getDataType());
            indValue2.setFieldValue("vertical", new StringFieldValue("Software Consulting"));
            indAn2 = new Annotation(industry, indValue2);
        }

        SpanTree tree = new SpanTree("test");
        SpanList root = (SpanList) tree.getRoot();
        AlternateSpanList branch = new AlternateSpanList();

        SpanNode span1 = new Span(0, 3);
        SpanNode span2 = new Span(1, 9);
        SpanNode span3 = new Span(12, 10);

        SpanNode span11 = new Span(0, 3);
        SpanNode span22 = new Span(1, 9);
        SpanNode span33 = new Span(12, 10);

        SpanList alternate1 = new SpanList();
        alternate1.add(span3);
        alternate1.add(span2);
        alternate1.add(span1);

        SpanList alternate2 = new SpanList();
        alternate2.add(span11);
        alternate2.add(span22);
        alternate2.add(span33);

        tree.annotate(span1, compAn1);
        tree.annotate(span2, personAn1);
        tree.annotate(span3, locAn1);
        tree.annotate(span1, indAn1);

        tree.annotate(span11, compAn2);
        tree.annotate(span22, personAn2);
        tree.annotate(span33, locAn2);
        tree.annotate(span11, indAn2);

        List<SpanNode> subtreeList1 = new ArrayList<>();
        subtreeList1.add(alternate1);

        List<SpanNode> subtreeList2 = new ArrayList<>();
        subtreeList2.add(alternate2);
        branch.addChildren(1, subtreeList1, 20.0d);
        branch.addChildren(2, subtreeList2, 50.0d);
        root.add(branch);

        StringFieldValue body = (StringFieldValue) document.getFieldValue(document.getDataType().getField("body"));
        body.setSpanTree(tree);
        document.setFieldValue(document.getDataType().getField("body"), body);
    }
}
