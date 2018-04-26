// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.Field;
import com.yahoo.document.StructDataType;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Contains code snippets that are used in the documentation. Not really a test case.
 *
 * @author Einar M R Rosenvinge
 */
public class DocTestCase {

    private class Processing {
        private Service getService() {
            return null;
        }
    }

    private class Service {
        private DocumentTypeManager getDocumentTypeManager() {
            return null;
        }
    }

    private Processing processing = null;

    @Test
    public void testSimple1() {
        StringFieldValue text = new StringFieldValue("<html><head><title>Diary</title></head><body>I live in San Francisco</body></html>");
                                                    //012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
        SpanList root = new SpanList();
        root.add(new Span(0, 19))
                .add(new Span(19, 5))
                .add(new Span(24, 21))
                .add(new Span(45, 23))
                .add(new Span(68, 14));

        SpanTree tree = new SpanTree("html", root);
        text.setSpanTree(tree);
    }

    public void simple2() {
        //the following line works inside process(Document, Arguments, Processing) in a DocumentProcessor
        AnnotationTypeRegistry atr = processing.getService().getDocumentTypeManager().getAnnotationTypeRegistry();

        StringFieldValue text = new StringFieldValue("<html><head><title>Diary</title></head><body>I live in San Francisco</body></html>");
                                                    //012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789
        AnnotationType textType = atr.getType("text");
        AnnotationType markup = atr.getType("markup");

        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("html", root);


        Span span1 = new Span(0, 19);
        root.add(span1);
        tree.annotate(span1, markup);

        Span span2 = new Span(19, 5);
        root.add(span2);
        tree.annotate(span2, textType);

        Span span3 = new Span(24, 21);
        root.add(span3);
        tree.annotate(span3, markup);

        Span span4 = new Span(45, 23);
        root.add(span4);
        tree.annotate(span4, textType);

        Span span5 = new Span(68, 14);
        root.add(span5);
        tree.annotate(span5, markup);


        text.setSpanTree(tree);
    }

    public void simple3() {
        //the following line works inside process(Document, Arguments, Processing) in a DocumentProcessor
        AnnotationTypeRegistry atr = processing.getService().getDocumentTypeManager().getAnnotationTypeRegistry();

        StringFieldValue text = new StringFieldValue("<html><head><title>Diary</title></head><body>I live in San Francisco</body></html>");
                                                    //012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789

        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("html", root);

        AnnotationType textType = atr.getType("text");
        AnnotationType beginTag = atr.getType("begintag");
        AnnotationType endTag = atr.getType("endtag");
        AnnotationType bodyType = atr.getType("body");
        AnnotationType headerType = atr.getType("header");

        SpanList header = new SpanList();
        {
            Span span1 = new Span(6, 6);
            Span span2 = new Span(12, 7);
            Span span3 = new Span(19, 5);
            Span span4 = new Span(24, 8);
            Span span5 = new Span(32, 7);
            header.add(span1)
                    .add(span2)
                    .add(span3)
                    .add(span4)
                    .add(span5);
            tree.annotate(span1, beginTag)
                    .annotate(span2, beginTag)
                    .annotate(span3, textType)
                    .annotate(span4, endTag)
                    .annotate(span5, endTag)
                    .annotate(header, headerType);
        }

        SpanList body = new SpanList();
        {
            Span span1 = new Span(39, 6);
            Span span2 = new Span(45, 23);
            Span span3 = new Span(68, 7);
            body.add(span1)
                    .add(span2)
                    .add(span3);
            tree.annotate(span1, beginTag)
                    .annotate(span2, textType)
                    .annotate(span3, endTag)
                    .annotate(body, bodyType);
        }

        {
            Span span1 = new Span(0, 6);
            Span span2 = new Span(75, 7);
            root.add(span1)
                    .add(header)
                    .add(body)
                    .add(span2);
            tree.annotate(span1, beginTag)
                    .annotate(span2, endTag);
        }

        text.setSpanTree(tree);
    }

    public void simple4() {
        //the following line works inside process(Document, Arguments, Processing) in a DocumentProcessor
        AnnotationTypeRegistry atr = processing.getService().getDocumentTypeManager().getAnnotationTypeRegistry();

        StringFieldValue text = new StringFieldValue("<html><head><title>Diary</title></head><body>I live in San Francisco</body></html>");
                                                    //012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789

        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("html", root);

        AnnotationType textType = atr.getType("text");
        AnnotationType beginTag = atr.getType("begintag");
        AnnotationType endTag = atr.getType("endtag");
        AnnotationType bodyType = atr.getType("body");
        AnnotationType headerType = atr.getType("header");
        AnnotationType cityType = atr.getType("city");

        Struct position = (Struct) cityType.getDataType().createFieldValue();
        position.setFieldValue("latitude", 37.774929);
        position.setFieldValue("longitude", -122.419415);
        Annotation city = new Annotation(cityType, position);

        SpanList header = new SpanList();
        {
            Span span1 = new Span(6, 6);
            Span span2 = new Span(12, 7);
            Span span3 = new Span(19, 5);
            Span span4 = new Span(24, 8);
            Span span5 = new Span(32, 7);
            header.add(span1)
                    .add(span2)
                    .add(span3)
                    .add(span4)
                    .add(span5);
            tree.annotate(span1, beginTag)
                    .annotate(span2, beginTag)
                    .annotate(span3, textType)
                    .annotate(span4, endTag)
                    .annotate(span4, endTag)
                    .annotate(header, headerType);
        }

        SpanList textNode = new SpanList();
        {
            Span span1 = new Span(45, 10);
            Span span2 = new Span(55, 13);
            textNode.add(span1)
                    .add(span2);
            tree.annotate(span2, city)
                    .annotate(textNode, textType);
        }

        SpanList body = new SpanList();
        {
            Span span1 = new Span(39, 6);
            Span span2 = new Span(68, 7);
            body.add(span1)
                    .add(textNode)
                    .add(span2);
            tree.annotate(span1, beginTag)
                    .annotate(span2, endTag)
                    .annotate(body, bodyType);
        }

        {
            Span span1 = new Span(0, 6);
            Span span2 = new Span(75, 7);
            root.add(span1)
                    .add(header)
                    .add(body)
                    .add(span2);
            tree.annotate(span1, beginTag)
                    .annotate(span2, endTag);
        }

        text.setSpanTree(tree);
    }

    public void simple5() {
        //the following two lines work inside process(Document, Arguments, Processing) in a DocumentProcessor
        DocumentTypeManager dtm = processing.getService().getDocumentTypeManager();
        AnnotationTypeRegistry atr = dtm.getAnnotationTypeRegistry();

        StringFieldValue text = new StringFieldValue("<body><p>I live in San </p>Francisco</body>");
                                                    //012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789

        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("html", root);

        StructDataType positionType = (StructDataType) dtm.getDataType("position");

        AnnotationType textType = atr.getType("text");
        AnnotationType beginTag = atr.getType("begintag");
        AnnotationType endTag = atr.getType("endtag");
        AnnotationType bodyType = atr.getType("body");
        AnnotationType paragraphType = atr.getType("paragraph");
        AnnotationType cityType = atr.getType("city");

        Struct position = new Struct(positionType);
        position.setFieldValue("latitude", 37.774929);
        position.setFieldValue("longitude", -122.419415);

        Annotation sanAnnotation = new Annotation(textType);
        Annotation franciscoAnnotation = new Annotation(textType);

        Struct positionWithRef = (Struct) cityType.getDataType().createFieldValue();
        positionWithRef.setFieldValue("position", position);

        Field referencesField = ((StructDataType) cityType.getDataType()).getField("references");
        Array<FieldValue> refList = new Array<FieldValue>(referencesField.getDataType());
        AnnotationReferenceDataType annRefType = (AnnotationReferenceDataType) ((ArrayDataType) referencesField.getDataType()).getNestedType();
        refList.add(new AnnotationReference(annRefType, sanAnnotation));
        refList.add(new AnnotationReference(annRefType, franciscoAnnotation));
        positionWithRef.setFieldValue(referencesField, refList);

        Annotation city = new Annotation(cityType, positionWithRef);

        SpanList paragraph = new SpanList();
        {
            Span span1 = new Span(6, 3);
            Span span2 = new Span(9, 10);
            Span span3 = new Span(19, 4);
            Span span4 = new Span(23, 4);
            paragraph.add(span1)
                    .add(span2)
                    .add(span3)
                    .add(span4);
            tree.annotate(span1, beginTag)
                    .annotate(span2, textType)
                    .annotate(span3, sanAnnotation)
                    .annotate(span4, endTag)
                    .annotate(paragraph, paragraphType);
        }

        {
            Span span1 = new Span(0, 6);
            Span span2 = new Span(27, 9);
            Span span3 = new Span(36, 8);
            root.add(span1)
                    .add(paragraph)
                    .add(span2)
                    .add(span3);

            tree.annotate(span1, beginTag)
                    .annotate(span2, franciscoAnnotation)
                    .annotate(span3, endTag)
                    .annotate(root, bodyType)
                    .annotate(city);
        }

        text.setSpanTree(tree);
    }

    public void simple6() {
        StringFieldValue text = new StringFieldValue("<html><head><title>Diary</title></head><body>I live in San Francisco</body></html>");
                                                    //012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789

        SpanTree tree = text.getSpanTree("html");
        SpanList root = (SpanList) tree.getRoot();
        //TODO: Note that the above could have been a Span or an AlternateSpanList!

        ListIterator<SpanNode> nodeIt = root.childIterator();

        AnnotationType beginTag = new AnnotationType("begintag");
        AnnotationType endTag = new AnnotationType("endtag");


        while (nodeIt.hasNext()) {
            SpanNode node = nodeIt.next();
            boolean nodeHadMarkupAnnotation = removeMarkupAnnotation(tree, node);
            if (nodeHadMarkupAnnotation) {
                nodeIt.remove();
                List<Span> replacementNodes = analyzeMarkup(tree, node, text, beginTag, endTag);
                for (SpanNode repl : replacementNodes) {
                    nodeIt.add(repl);
                }
            }
        }
    }

    /**
     * Removes annotations of type 'markup' from the given node.
     *
     * @param tree the tree to remove annotations from
     * @param node the node to remove annotations of type 'markup' from
     * @return true if the given node had 'markup' annotations, false otherwise
     */
    private boolean removeMarkupAnnotation(SpanTree tree, SpanNode node) {
        //get iterator over all annotations on this node:
        Iterator<Annotation> annotationIt = tree.iterator(node);

        while (annotationIt.hasNext()) {
            Annotation annotation = annotationIt.next();
            if (annotation.getType().getName().equals("markup")) {
                //this node has an annotation of type markup, remove it:
                annotationIt.remove();
                //return true, this node had a markup annotation:
                return true;
            }
        }
        //this node did not have a markup annotation:
        return false;
    }

    /**
     * NOTE: This method is provided only for completeness. It analyzes spans annotated with
     * &quot;markup&quot;, and splits them into several shorter spans annotated with &quot;begintag&quot;
     * and &quot;endtag&quot;.
     *
     * @param tree       the span tree to annotate into
     * @param input      a SpanNode that is annotated with &quot;markup&quot;.
     * @param text       the text that the SpanNode covers
     * @param beginTag   the type to use for begintag annotations
     * @param endTagType the type to use for endtag annotations
     * @return a list of new spans to replace the input
     */
    private List<Span> analyzeMarkup(SpanTree tree, SpanNode input, StringFieldValue text,
                                     AnnotationType beginTag, AnnotationType endTagType) {
        //we know that this node is annotated with "markup"
        String coveredText = input.getText(text.getString()).toString();
        int spanOffset = input.getFrom();
        int tagStart = -1;
        boolean endTag = false;
        List<Span> tags = new ArrayList<Span>();
        for (int i = 0; i < coveredText.length(); i++) {
            if (coveredText.charAt(i) == '<') {
                //we're in a tag
                tagStart = i;
                continue;
            }
            if (coveredText.charAt(i) == '>' && tagStart > -1) {
                Span span = new Span(spanOffset + tagStart, (i + 1) - tagStart);
                tags.add(span);
                if (endTag) {
                    tree.annotate(span, endTagType);
                } else {
                    tree.annotate(span, beginTag);
                }
                tagStart = -1;
            }
            if (tagStart > -1 && i == (tagStart + 1)) {
                if (coveredText.charAt(i) == '/') {
                    endTag = true;
                } else {
                    endTag = false;
                }
            }
        }
        return tags;
    }

    public void simple7() {
        StringFieldValue text = new StringFieldValue("<html><head><title>Diary</title></head><body>I live in San Francisco</body></html>");
                                                    //012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789

        SpanTree tree = text.getSpanTree("html");

        Iterator<Annotation> annotationIt = tree.iterator();

        while (annotationIt.hasNext()) {
            Annotation annotation = annotationIt.next();
            if (annotation.getType().getName().equals("markup")) {
                //we have an annotation of type markup, remove it:
                annotationIt.remove();
            }
        }
    }

}
