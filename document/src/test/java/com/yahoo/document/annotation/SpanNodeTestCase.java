// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.annotation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Einar M R Rosenvinge
 */
public class SpanNodeTestCase {

    @Test
    public void testOverlaps() {
        //qwertyuiopasdfghjklzxcvbnm
        //012345678901234567890123456789
        Span a = new Span(0, 6);  //qwerty
        Span b = new Span(6, 6);  //uiopas
        Span c = new Span(12, 5); //dfghj
        Span d = new Span(17, 6); //klzxcv
        Span e = new Span(23, 3); //bnm

        assertFalse(a.overlaps(b));
        assertFalse(b.overlaps(a));

        assertFalse(b.overlaps(c));
        assertFalse(c.overlaps(b));

        assertFalse(c.overlaps(d));
        assertFalse(d.overlaps(c));

        assertFalse(d.overlaps(e));
        assertFalse(e.overlaps(d));


        Span all = new Span(0, 25);

        assertTrue(a.overlaps(all));
        assertTrue(all.overlaps(a));

        assertTrue(b.overlaps(all));
        assertTrue(all.overlaps(b));

        assertTrue(c.overlaps(all));
        assertTrue(all.overlaps(c));

        assertTrue(all.overlaps(d));
        assertTrue(d.overlaps(all));

        assertTrue(all.overlaps(e));
        assertTrue(e.overlaps(all));


        Span f = new Span(3, 7); // rtyuiop

        assertTrue(a.overlaps(f));
        assertTrue(f.overlaps(a));

        assertTrue(b.overlaps(f));
        assertTrue(f.overlaps(b));

        assertFalse(c.overlaps(f));
        assertFalse(f.overlaps(c));

        assertFalse(d.overlaps(f));
        assertFalse(f.overlaps(d));

        assertFalse(e.overlaps(f));
        assertFalse(f.overlaps(e));

        assertTrue(all.overlaps(f));
        assertTrue(f.overlaps(all));


        Span g = new Span(0, 7);  //qwertyu

        assertTrue(a.overlaps(g));
        assertTrue(g.overlaps(a));

        assertTrue(b.overlaps(g));
        assertTrue(g.overlaps(b));

        assertFalse(c.overlaps(g));
        assertFalse(g.overlaps(c));

        assertFalse(d.overlaps(g));
        assertFalse(g.overlaps(d));

        assertFalse(e.overlaps(g));
        assertFalse(g.overlaps(e));

        assertTrue(f.overlaps(g));
        assertTrue(g.overlaps(f));

        assertTrue(all.overlaps(g));
        assertTrue(g.overlaps(all));


        assertTrue(a.overlaps(a));
    }

    @Test
    public void testContains() {
        //qwertyuiopasdfghjklzxcvbnm
        //012345678901234567890123456789
        Span a = new Span(0, 6);  //qwerty
        Span b = new Span(6, 6);  //uiopas
        Span all = new Span(0, 25);

        assertFalse(a.contains(all));
        assertTrue(all.contains(a));

        assertFalse(a.contains(b));
        assertFalse(b.contains(a));

        assertTrue(a.contains(a));

        Span c = new Span(0, 7);  //qwertyu

        assertFalse(a.contains(c));
        assertTrue(c.contains(a));
    }

    @Test
    public void testSpanTree() {
        final String text = "Hallo er et ord fra Norge";
        SpanList sentence = new SpanList();
        SpanTree tree = new SpanTree("sentence", sentence);

        //Locate words and whitespace:
        SpanNode hallo = new Span(0, 5);
        SpanNode spc1 = new Span(5, 1);
        SpanNode er = new Span(6, 2);
        SpanNode spc2 = new Span(8, 1);
        SpanNode et = new Span(9, 2);
        SpanNode spc3 = new Span(11, 1);
        SpanNode ord = new Span(12, 3);
        SpanNode spc4 = new Span(15, 1);
        SpanNode fra = new Span(16, 3);
        SpanNode spc5 = new Span(19, 1);
        SpanNode norge = new Span(20, 5);

        AnnotationType noun = new AnnotationType("noun");
        AnnotationType verb = new AnnotationType("verb");
        AnnotationType prep = new AnnotationType("preposition");
        AnnotationType det = new AnnotationType("determiner");
        AnnotationType noun_phrase = new AnnotationType("noun_phrase");
        AnnotationType verb_phrase = new AnnotationType("verb_phrase");
        AnnotationType prep_phrase = new AnnotationType("preposition_phrase");
        AnnotationType separator = new AnnotationType("separator");
        AnnotationType sentenceType = new AnnotationType("sentence");

        //Determine word classes and add annotation for them:
        tree.annotate(hallo, noun);
        tree.annotate(spc1, separator);
        tree.annotate(er, verb);
        tree.annotate(spc2, separator);
        tree.annotate(et, det);
        tree.annotate(spc3, separator);
        tree.annotate(ord, noun);
        tree.annotate(spc4, separator);
        tree.annotate(fra, prep);
        tree.annotate(spc5, separator);
        tree.annotate(norge, noun);


        //Identify phrases and build natural language parse tree, and annotate as we go:
        tree.annotate(hallo, noun_phrase);

        SpanList np2 = new SpanList();
        np2.children().add(et);
        np2.children().add(spc3);
        np2.children().add(ord);
        tree.annotate(np2, noun_phrase);

        tree.annotate(norge, noun_phrase);

        SpanList pp = new SpanList();
        pp.children().add(fra);
        pp.children().add(spc5);
        pp.children().add(norge);
        tree.annotate(pp, prep_phrase);

        SpanList np3 = new SpanList();
        np3.children().add(np2);
        np3.children().add(spc4);
        np3.children().add(pp);
        tree.annotate(np3, noun_phrase);

        SpanList vp = new SpanList();
        vp.children().add(er);
        vp.children().add(spc2);
        vp.children().add(np3);
        tree.annotate(vp, verb_phrase);

        sentence.children().add(hallo);
        sentence.children().add(spc1);
        sentence.children().add(vp);
        tree.annotate(sentence, sentenceType);


        //assert that extracted text is correct:
        assertEquals("Hallo", hallo.getText(text));
        assertEquals(" ", spc1.getText(text));
        assertEquals("er", er.getText(text));
        assertEquals(" ", spc2.getText(text));
        assertEquals("et", et.getText(text));
        assertEquals(" ", spc3.getText(text));
        assertEquals("ord", ord.getText(text));
        assertEquals(" ", spc4.getText(text));
        assertEquals("fra", fra.getText(text));
        assertEquals(" ", spc5.getText(text));
        assertEquals("Norge", norge.getText(text));

        assertEquals("et ord", np2.getText(text).toString());
        assertEquals("fra Norge", pp.getText(text).toString());
        assertEquals("et ord fra Norge", np3.getText(text).toString());
        assertEquals("er et ord fra Norge", vp.getText(text).toString());
        assertEquals("Hallo er et ord fra Norge", sentence.getText(text).toString());

        //coming up: assert that children(Annotation) works...
    }

    @Test
    public void testOrder() {
        {
            String text = "08/20/1999";
                         //012345678901
            Span d = new Span(3, 2);
            Span m = new Span(0, 2);
            Span y = new Span(6, 4);

            SpanList date = new SpanList();
            date.children().add(d);
            date.children().add(m);
            date.children().add(y);

            assertEquals("20081999", date.getText(text).toString());

            assertEquals(0, date.getFrom());
            assertEquals(10, date.getTo());
            assertEquals(10, date.getLength());
        }
        {
            String text = "20/08/2000";
            //012345678901
            Span d = new Span(0, 2);
            Span m = new Span(3, 2);
            Span y = new Span(6, 4);

            SpanList date = new SpanList();
            date.children().add(d);
            date.children().add(m);
            date.children().add(y);

            assertEquals("20082000", date.getText(text).toString());

            assertEquals(0, date.getFrom());
            assertEquals(10, date.getTo());
            assertEquals(10, date.getLength());
        }
    }

    @Test
    public void testNonRecursiveAnnotationIterator() {
        AnnotationType nounType = new AnnotationType("noun");
        AnnotationType detType = new AnnotationType("determiner");
        AnnotationType wordType = new AnnotationType("word");
        AnnotationType begTagType = new AnnotationType("begin_tag");
        AnnotationType cmpWordType = new AnnotationType("compound_word");

        SpanNode span = new Span(0,2);
        SpanTree tree = new SpanTree("span", span);

        Annotation word = new Annotation(wordType);
        Annotation bgtg = new Annotation(begTagType);
        Annotation cpwd = new Annotation(cmpWordType);
        Annotation detr = new Annotation(detType);
        Annotation noun = new Annotation(nounType);

        tree.annotate(span, word);
        tree.annotate(span, bgtg);
        tree.annotate(span, cpwd);
        tree.annotate(span, detr);
        tree.annotate(span, noun);


        {
            Iterator<Annotation> it = tree.iterator();
            assertSame(word, it.next());
            assertSame(bgtg, it.next());
            assertSame(cpwd, it.next());
            assertSame(detr, it.next());
            assertSame(noun, it.next());
            try {
                it.next();
                fail("Should have gotten NoSuchElementException");
            } catch (NoSuchElementException e) {
                //ignore
            }
        }
        {
            Iterator<Annotation> it = tree.iterator();
            for (int i = 0; i < 100; i++) {
                assertTrue(it.hasNext());
            }
        }
        {
            Iterator<Annotation> it = tree.iterator();
            assertTrue(it.hasNext());
            assertSame(word, it.next());
            assertTrue(it.hasNext());
            assertSame(bgtg, it.next());
            assertTrue(it.hasNext());
            assertSame(cpwd, it.next());
            assertTrue(it.hasNext());
            assertSame(detr, it.next());
            assertTrue(it.hasNext());
            assertSame(noun, it.next());
            assertFalse(it.hasNext());
        }



        {
            Iterator<Annotation> it = tree.iterator();
            assertSame(word, it.next());
            assertSame(bgtg, it.next());
            assertSame(cpwd, it.next());
            assertSame(detr, it.next());
            assertSame(noun, it.next());
            try {
                it.next();
                fail("Should have gotten NoSuchElementException");
            } catch (NoSuchElementException e) {
                //ignore
            }
        }
        {
            Iterator<Annotation> it = tree.iterator();
            for (int i = 0; i < 100; i++) {
                assertTrue(it.hasNext());
            }
        }
        {
            Iterator<Annotation> it = tree.iterator();
            assertTrue(it.hasNext());
            assertSame(word, it.next());
            assertTrue(it.hasNext());
            assertSame(bgtg, it.next());
            assertTrue(it.hasNext());
            assertSame(cpwd, it.next());
            assertTrue(it.hasNext());
            assertSame(detr, it.next());
            assertTrue(it.hasNext());
            assertSame(noun, it.next());
            assertFalse(it.hasNext());
        }

    }

    @Test
    public void testRecursion() {
        AnnotationType noun = new AnnotationType("noun");
        AnnotationType verb = new AnnotationType("verb");
        AnnotationType sentenceType = new AnnotationType("sentence");
        AnnotationType word = new AnnotationType("word");
        AnnotationType phraseType = new AnnotationType("phrase");

        String text = "There is no bizniz like showbizniz";
                     //0123456789012345678901234567890123456789
        SpanList sentence = new SpanList();
        SpanTree tree = new SpanTree("sentence", sentence);

        Span there = new Span(0, 5);
        Span is = new Span(6, 2);
        Span no = new Span(9, 2);
        Span bizniz = new Span(12, 6);
        Span like = new Span(19, 4);
        Span showbizniz = new Span(24, 10);

        tree.annotate(there, word);
        tree.annotate(is, word);
        tree.annotate(is, verb);
        tree.annotate(no, word);
        tree.annotate(bizniz, word);
        tree.annotate(bizniz, noun);
        tree.annotate(like, word);
        tree.annotate(showbizniz, word);
        tree.annotate(showbizniz, noun);

        SpanList endPhrase = new SpanList();
        endPhrase.add(like);
        endPhrase.add(showbizniz);
        tree.annotate(endPhrase, phraseType);

        SpanList phrase = new SpanList();
        phrase.children().add(no);
        phrase.children().add(bizniz);
        phrase.children().add(endPhrase);
        tree.annotate(phrase, phraseType);

        sentence.children().add(there);
        sentence.children().add(is);
        sentence.children().add(phrase);
        tree.annotate(sentence, sentenceType);

        Iterator<SpanNode> children;

        children = sentence.childIteratorRecursive();

        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("There", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(1, a.size());
            assertEquals(a.get(0).getType(), word);
        }
        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("is", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(2, a.size());
            assertEquals(a.get(0).getType(), word);
            assertEquals(a.get(1).getType(), verb);
        }
        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("no", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(1, a.size());
            assertEquals(a.get(0).getType(), word);
        }
        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("bizniz", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(2, a.size());
            assertEquals(a.get(0).getType(), word);
            assertEquals(a.get(1).getType(), noun);
        }
        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("like", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(1, a.size());
            assertEquals(a.get(0).getType(), word);
        }
        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("showbizniz", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(2, a.size());
            assertEquals(a.get(0).getType(), word);
            assertEquals(a.get(1).getType(), noun);
        }
        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("likeshowbizniz", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(1, a.size());
            assertEquals(a.get(0).getType(), phraseType);
        }
        {
            assertTrue(children.hasNext());
            SpanNode next = children.next();
            assertEquals("nobiznizlikeshowbizniz", next.getText(text).toString());
            List<Annotation> a = annotations(tree, next);
            assertEquals(1, a.size());
            assertEquals(a.get(0).getType(), phraseType);
        }
        {
            assertFalse(children.hasNext());
        }

        List<Annotation> annotationList = new LinkedList<Annotation>();
        for (Annotation a : tree) {
            annotationList.add(a);
        }
        Collections.sort(annotationList);

        Iterator<Annotation> annotations = annotationList.iterator();

        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("There", annotation.getSpanNode().getText(text));
        assertEquals(word, annotation.getType());  //there: word
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("Thereisnobiznizlikeshowbizniz", annotation.getSpanNode().getText(text).toString());
        assertEquals(sentenceType, annotation.getType());  //sentence
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("is", annotation.getSpanNode().getText(text));
        assertEquals(word, annotation.getType());  //is: word
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("is", annotation.getSpanNode().getText(text));
        assertEquals(verb, annotation.getType());  //is: verb
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("no", annotation.getSpanNode().getText(text));
        assertEquals(word, annotation.getType());  //no: word
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("nobiznizlikeshowbizniz", annotation.getSpanNode().getText(text).toString());
        assertEquals(phraseType, annotation.getType());  //no bizniz like showbizniz: phrase
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("bizniz", annotation.getSpanNode().getText(text));
        assertEquals(word, annotation.getType());  //bizniz: word
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("bizniz", annotation.getSpanNode().getText(text));
        assertEquals(noun, annotation.getType());  //bizniz: noun
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("like", annotation.getSpanNode().getText(text));
        assertEquals(word, annotation.getType());  //like: word
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("likeshowbizniz", annotation.getSpanNode().getText(text).toString());
        assertEquals(phraseType, annotation.getType());  //like showbizniz: phrase
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("showbizniz", annotation.getSpanNode().getText(text));
        assertEquals(word, annotation.getType());  //showbizniz: word
        }
        {
        assertTrue(annotations.hasNext());
        Annotation annotation = annotations.next();
        assertEquals("showbizniz", annotation.getSpanNode().getText(text));
        assertEquals(noun, annotation.getType());  //showbizniz: noun
        }

        assertFalse(annotations.hasNext());
    }

    private static List<Annotation> annotations(SpanTree tree, SpanNode node) {
        List<Annotation> list = new ArrayList<>();
        Iterator<Annotation> it = tree.iterator(node);
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list;
    }

    @Test
	public void testMultilevelRecursion() {
		//			   01234567890123
		String text = "Hello!Goodbye!";
        AnnotationType block = new AnnotationType("block");
		SpanList root = new SpanList();
        SpanTree tree = new SpanTree("root", root);

		SpanList block1 = new SpanList();
		SpanNode hello = new Span(0,6);
        tree.annotate(hello, block);
		block1.add(hello);

		SpanList block2 = new SpanList();
		SpanNode goodbye = new Span(6,8);
        tree.annotate(goodbye, block);
		block2.add(goodbye);

		root.add(block1).add(block2);

		Iterator<SpanNode> nodeIterator = root.childIteratorRecursive();
		assertTrue(nodeIterator.hasNext());
		assertTrue(nodeIterator.next().equals(hello));
		assertTrue(nodeIterator.hasNext());
		assertTrue(nodeIterator.next().equals(block1));
		assertTrue(nodeIterator.hasNext());
		assertTrue(nodeIterator.next().equals(goodbye));
		assertTrue(nodeIterator.hasNext());
		assertTrue(nodeIterator.next().equals(block2));
		assertFalse(nodeIterator.hasNext());
		assertTrue(root.getText(text).toString().equals(text));
	}

    @Test
    public void testRecursiveIteratorDeterministicBehavior() {
        SpanList root = new SpanList();
        SpanTree tree = new SpanTree("tree", root);

        SpanList a = new SpanList();
        Span b = new Span(0,1);
        Span c = new Span(1,1);
        a.add(b).add(c);
        root.add(a);
        Span d = new Span(2,1);
        root.add(d);

        Span newC = new Span(3,1);

        ListIterator<SpanNode> children = root.childIteratorRecursive();
        assertTrue(children.hasNext());
        assertSame(b, children.next());
        assertTrue(children.hasNext());
        assertSame(c, children.next());
        assertTrue(children.hasNext());
        assertTrue(children.hasNext());
        assertTrue(children.hasNext());

        children.set(newC);
        assertTrue(children.hasNext());
        assertSame(a, children.next());
        assertTrue(children.hasNext());
        assertSame(d, children.next());
        assertFalse(children.hasNext());


        assertSame(a, root.children().get(0));
        assertSame(d, root.children().get(1));
        assertEquals(2, root.children().size());

        assertSame(b, a.children().get(0));
        assertSame(newC, a.children().get(1));
        assertEquals(2, a.children().size());
    }

}
