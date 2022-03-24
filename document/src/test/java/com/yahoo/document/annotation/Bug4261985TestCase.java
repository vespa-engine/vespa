// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.io.GrowableByteBuffer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class Bug4261985TestCase {

    @Test
    public void testAnnotate() {
        DocumentTypeManager manager = new DocumentTypeManager();
        var sub = DocumentTypeManagerConfigurer.configure
            (manager, "file:src/test/java/com/yahoo/document/annotation/documentmanager.bug4261985.cfg");
        sub.close();

        DocumentType type = manager.getDocumentType("blog");
        Document doc = new Document(type, "id:this:blog::is:a:test");
        doc.setFieldValue("body", new StringFieldValue("bla bla bla bla bla bla bla" +
                                                       "bla bla bla bla bla bla bla"));
        annotate(doc, manager);

        GrowableByteBuffer buf = new GrowableByteBuffer();
        doc.serialize(buf);
    }

    public void annotate(Document document, DocumentTypeManager manager) {
        DocumentType docType = manager.getDocumentType("blog");
        AnnotationTypeRegistry registry = manager.getAnnotationTypeRegistry();

        AnnotationType company = registry.getType("company");
        AnnotationType industry = registry.getType("industry");
        AnnotationType person  = registry.getType("person");
        AnnotationType location = registry.getType("location");
        AnnotationType bigshots = registry.getType("bigshots");

        if (company.inherits(industry)) {
            System.out.println("Company Inherits Industry");
        } else {
            System.out.println("FAIL: COMPANY DOES NOT INHERIT INDUSTRY");
            throw new RuntimeException("FAIL: COMPANY DOES NOT INHERIT INDUSTRY, though it does in SD file");
        }

        SpanTree tree = new SpanTree("testannotations");
        SpanList root = (SpanList) tree.getRoot();

        SpanNode span1 = new Span(0,5);
        SpanNode span2 = new Span(5,10);
        SpanNode span3 = new Span(10,15);
        SpanNode span4 = new Span(15,20);
        SpanNode span5 = new Span(6,10);
        SpanNode span6 = new Span(8,4);
        SpanNode span7 = new Span(4, 2);
        SpanNode span8 = new Span(12, 6);

        root.add(span1);
        root.add(span2);
        //root.add(span3);
        root.add(span4);
        root.add(span5);
        root.add(span6);
        //root.add(span7);
        root.add(span8);

        AlternateSpanList aspl = new AlternateSpanList();
        aspl.add(span7);
        List<SpanNode> subtree1 = new ArrayList<SpanNode>();
        subtree1.add(span3);
        aspl.addChildren(1, subtree1, 33.0d);

        root.add(aspl);

        Struct personValue = (Struct) person.getDataType().createFieldValue();
        personValue.setFieldValue("name", "Richard Bair");
        Annotation personAn = new Annotation(person, personValue);
        tree.annotate(span1, personAn);

        Struct companyValue = (Struct) company.getDataType().createFieldValue();
        companyValue.setFieldValue("name", "Sun");

        Struct locationVal = new Struct(location.getDataType());
        locationVal.setFieldValue("lat", 37.774929);
        locationVal.setFieldValue("lon", -122.419415);
        Annotation locAnnotation = new Annotation(location, locationVal);
        Field compLocField = ((StructDataType) company.getDataType()).getField("place");
        //FieldValue compLocFieldVal = new FieldValue(compLocField.getDataType());
        AnnotationReferenceDataType annType = (AnnotationReferenceDataType) compLocField.getDataType();
        FieldValue compLocFieldVal = null;
        //if (scenario.equals("createFieldValue")) {
        //   compLocFieldVal = annType.createFieldValue(new AnnotationReference(annType, locAnnotation));
        //} else {
        compLocFieldVal = new AnnotationReference(annType, locAnnotation);
        //}
        companyValue.setFieldValue(compLocField, compLocFieldVal);

        companyValue.setFieldValue("vertical", "software");
        Struct dirValue1 = new Struct(person.getDataType());
        dirValue1.setFieldValue("name", "Jonathan Schwartz");
        Annotation dirAnnotation1 = new Annotation(person, dirValue1);
        Struct dirValue2 = new Struct(person.getDataType());
        dirValue2.setFieldValue("name", "Scott Mcnealy");
        Annotation dirAnnotation2 = new Annotation(person, dirValue2);
        Field dirField = ((StructDataType) company.getDataType()).getField("directors");
        Array<FieldValue> dirFieldVal = new Array<FieldValue>(dirField.getDataType());
        AnnotationReferenceDataType annRefType = (AnnotationReferenceDataType) ((ArrayDataType) dirField.getDataType()).getNestedType();
        dirFieldVal.add(new AnnotationReference(annRefType, dirAnnotation1));
        dirFieldVal.add(new AnnotationReference(annRefType, dirAnnotation2));
        companyValue.setFieldValue(dirField, dirFieldVal);
        Annotation compAn = new Annotation(company, companyValue);
        tree.annotate(span2, compAn);

        Struct bigshotsValue = (Struct) bigshots.getDataType().createFieldValue();
        Field ceosField = ((StructDataType) bigshots.getDataType()).getField("ceos");
        //FieldValue compLocFieldVal = new FieldValue(compLocField.getDataType());
        AnnotationReferenceDataType annType1 = (AnnotationReferenceDataType) ceosField.getDataType();
        FieldValue ceosFieldVal = new AnnotationReference(annType1, compAn);
        bigshotsValue.setFieldValue(ceosField, ceosFieldVal);

        Annotation bigshotsAn = new Annotation(bigshots, bigshotsValue);
        tree.annotate(span8, bigshotsAn);

        Field selfField = ((StructDataType) bigshots.getDataType()).getField("self");
        AnnotationReferenceDataType annType2 = (AnnotationReferenceDataType) selfField.getDataType();
        FieldValue selfFieldVal = new AnnotationReference(annType2, bigshotsAn);
        bigshotsValue.setFieldValue(selfField, selfFieldVal);
        bigshotsAn = new Annotation(bigshots, bigshotsValue);
        tree.annotate(span8, bigshotsAn);

        tree.annotate(span3, locAnnotation);
        tree.annotate(span5, dirAnnotation1);
        tree.annotate(span6, dirAnnotation2);

        Struct indValue = new Struct(industry.getDataType());
        indValue.setFieldValue("vertical", "Manufacturing");
        Annotation indAn = new Annotation(industry, indValue);
        tree.annotate(span4, indAn);

        StringFieldValue body = (StringFieldValue) document.getFieldValue(document.getDataType().getField("body"));
        body.setSpanTree(tree);
        document.setFieldValue(document.getDataType().getField("body"), body);
    }
}

