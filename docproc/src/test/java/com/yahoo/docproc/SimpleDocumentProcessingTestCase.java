// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.docproc.Accesses.Field.Tree;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Tests the basic operation of the docproc service
 *
 * @author bratseth
 */
public class SimpleDocumentProcessingTestCase extends DocumentProcessingAbstractTestCase {

    /**
     * Tests chaining of some processors, and execution of the processors
     * on some documents
     */
    @Test
    public void testSimpleProcessing() {
        // Set up service programmatically
        DocprocService service = new DocprocService("simple");
        DocumentProcessor first = new TestDocumentProcessor1();
        DocumentProcessor second = new TestDocumentProcessor2();
        DocumentProcessor third = new TestDocumentProcessor3();
        service.setCallStack(new CallStack().addLast(first).addLast(second).addLast(third));
        service.setInService(true);

        assertProcessingWorks(service);
    }

    @Test
    public void testAnnotationBasic() {
        Accesses accesses = MyDocProc.class.getAnnotation(Accesses.class);
        After after = MyDocProc.class.getAnnotation(After.class);
        assertNotNull(accesses);
        assertNotNull(after);
        assertEquals(after.value()[0], "MyOtherDocProc");
        assertEquals(after.value()[1], "AnotherDocProc");
        assertEquals(accesses.value()[0].name(), "myField1");
        assertEquals(accesses.value()[1].annotations()[0].consumes()[0], "word");
        assertEquals(accesses.value()[1].annotations()[0].consumes()[1], "phrase");
    }

    @Accesses({ @Accesses.Field(name = "myField1", dataType = "string",
                                description = "What is done on field myField1",
                                annotations = @Tree(produces = { "word", "sentence" }, consumes = "word")),
                @Accesses.Field(name = "myField2", dataType = "string",
                                description = "What is done on field myField2",
                                annotations = @Tree(consumes = { "word", "phrase" })) })
    @After({ "MyOtherDocProc", "AnotherDocProc" })
    public static class MyDocProc extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            // TODO Auto-generated method stub
            return null;
        }
    }

}

