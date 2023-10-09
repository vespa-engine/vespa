// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public abstract class AbstractTypesTest {

    protected DocumentTypeManager man;
    protected StructDataType person;
    protected AnnotationReferenceDataType personReference;
    protected StructDataType relative;
    protected AnnotationType dummy;
    protected AnnotationType number;
    protected AnnotationType personA;
    protected AnnotationType relativeA;
    protected AnnotationType banana;
    protected AnnotationType apple;
    protected AnnotationType grape;
    protected DocumentType docType;

    public AbstractTypesTest() {
        man = new DocumentTypeManager();

        person = new StructDataType("person");
        person.addField(new Field("firstname", DataType.STRING));
        person.addField(new Field("lastname", DataType.STRING));
        person.addField(new Field("birthyear", DataType.INT));
        man.register(person);

        personA = new AnnotationType("person", person);
        man.getAnnotationTypeRegistry().register(personA);

        personReference = new AnnotationReferenceDataType(personA);
        man.register(personReference);

        relative = new StructDataType("relative");
        relative.addField(new Field("title", DataType.STRING));
        relative.addField(new Field("related", personReference));
        man.register(relative);

        dummy = new AnnotationType("dummy");
        number = new AnnotationType("number", DataType.INT);
        relativeA = new AnnotationType("relative", relative);
        banana = new AnnotationType("banana");
        apple = new AnnotationType("apple");
        grape = new AnnotationType("grape");
        man.getAnnotationTypeRegistry().register(dummy);
        man.getAnnotationTypeRegistry().register(number);
        man.getAnnotationTypeRegistry().register(relativeA);
        man.getAnnotationTypeRegistry().register(banana);
        man.getAnnotationTypeRegistry().register(apple);
        man.getAnnotationTypeRegistry().register(grape);

        docType = new DocumentType("dokk");
        docType.addField("age", DataType.BYTE);
        docType.addField("story", DataType.STRING);
        docType.addField("date", DataType.INT);
        docType.addField("friend", DataType.LONG);
        man.register(docType);
    }

    protected StringFieldValue getAnnotatedString() {
        StringFieldValue text = new StringFieldValue("help me help me i'm stuck inside a computer!");
        {
            AlternateSpanList alternateSpanList = new AlternateSpanList();
            SpanTree tree = new SpanTree("ballooo", alternateSpanList);
            text.setSpanTree(tree);

            Span s1 = new Span(1, 2);
            Span s2 = new Span(3, 4);
            Span s3 = new Span(4, 5);
            alternateSpanList.add(s1).add(s2).add(s3);
            AlternateSpanList s4 = new AlternateSpanList();
            Span s5 = new Span(7, 8);
            Span s6 = new Span(8, 9);
            s4.add(s5).add(s6);
            alternateSpanList.add(s4);

            tree.annotate(s2, dummy);
            tree.annotate(s2, new Annotation(number, new IntegerFieldValue(1234)));

            Struct mother = new Struct(person);
            mother.setFieldValue("firstname", "jenny");
            mother.setFieldValue("lastname", "olsen");
            mother.setFieldValue("birthyear", 1909);
            Annotation motherA = new Annotation(personA, mother);
            tree.annotate(s2, motherA);

            Struct daughter = new Struct(relative);
            daughter.setFieldValue("title", "daughter");
            daughter.setFieldValue("related", new AnnotationReference(personReference, motherA));
            tree.annotate(s6, new Annotation(relativeA, daughter));

            tree.annotate(s1, dummy);
            tree.annotate(s3, dummy);
            tree.annotate(s3, new Annotation(number, new IntegerFieldValue(2344)));
            tree.annotate(s5, dummy);

            List<SpanNode> alternateChildren = new ArrayList<>();
            Span s7 = new Span(1, 4);
            Span s8 = new Span(1, 9);
            Span s9 = new Span(5, 6);
            alternateChildren.add(s7);
            alternateChildren.add(s8);
            alternateChildren.add(s9);

            alternateSpanList.addChildren(alternateChildren, 5.55);
        }
        {
            SpanList root = new SpanList();
            SpanTree tree = new SpanTree("fruits", root);
            text.setSpanTree(tree);


            Span s1 = new Span(5, 6);
            Span s2 = new Span(11, 3);
            Span s3 = new Span(14, 7);
            root.add(s1).add(s2).add(s3);

            tree.annotate(s1, banana);
            tree.annotate(s1, grape);
            tree.annotate(s2, banana);
            tree.annotate(s3, apple);
            tree.annotate(s3, grape);
            tree.annotate(s3, grape);

            SpanList s4 = new SpanList();
            Span s5 = new Span(23,1);
            s4.add(s5);
            root.add(s4);

            tree.annotate(s4, grape);
            tree.annotate(s5, apple);
        }
        return text;
    }
}
