// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.docproc.Processing;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import org.junit.Test;

import java.net.URI;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class DocumentProcessingTaskPrioritizationTestCase {

    @Test
    public void proritization() {
        Queue<DocumentProcessingTask> queue = new PriorityBlockingQueue<>();

        DocumentProcessingTask highest = new TestDocumentProcessingTask(DocumentProtocol.Priority.HIGHEST);
        DocumentProcessingTask veryhigh = new TestDocumentProcessingTask(DocumentProtocol.Priority.VERY_HIGH);
        DocumentProcessingTask high1 = new TestDocumentProcessingTask(DocumentProtocol.Priority.HIGH_1);
        DocumentProcessingTask normal_1 = new TestDocumentProcessingTask(DocumentProtocol.Priority.NORMAL_1);
        DocumentProcessingTask low_1 = new TestDocumentProcessingTask(DocumentProtocol.Priority.LOW_1);
        DocumentProcessingTask verylow = new TestDocumentProcessingTask(DocumentProtocol.Priority.VERY_LOW);
        DocumentProcessingTask lowest = new TestDocumentProcessingTask(DocumentProtocol.Priority.LOWEST);

        DocumentProcessingTask normal_2 = new TestDocumentProcessingTask(DocumentProtocol.Priority.NORMAL_1);
        DocumentProcessingTask normal_3 = new TestDocumentProcessingTask(DocumentProtocol.Priority.NORMAL_1);
        DocumentProcessingTask normal_4 = new TestDocumentProcessingTask(DocumentProtocol.Priority.NORMAL_1);

        DocumentProcessingTask highest_2 = new TestDocumentProcessingTask(DocumentProtocol.Priority.HIGHEST);
        DocumentProcessingTask highest_3 = new TestDocumentProcessingTask(DocumentProtocol.Priority.HIGHEST);


        queue.add(highest);
        queue.add(veryhigh);
        queue.add(high1);
        queue.add(normal_1);
        queue.add(low_1);
        queue.add(verylow);
        queue.add(lowest);

        queue.add(normal_2);
        queue.add(normal_3);
        queue.add(normal_4);

        queue.add(highest_2);
        queue.add(highest_3);

        assertThat(queue.poll(), sameInstance(highest));
        assertThat(queue.poll(), sameInstance(highest_2));
        assertThat(queue.poll(), sameInstance(highest_3));
        assertThat(queue.poll(), sameInstance(veryhigh));
        assertThat(queue.poll(), sameInstance(high1));
        assertThat(queue.poll(), sameInstance(normal_1));
        assertThat(queue.poll(), sameInstance(normal_2));
        assertThat(queue.poll(), sameInstance(normal_3));
        assertThat(queue.poll(), sameInstance(normal_4));
        assertThat(queue.poll(), sameInstance(low_1));
        assertThat(queue.poll(), sameInstance(verylow));
        assertThat(queue.poll(), sameInstance(lowest));
        assertThat(queue.poll(), nullValue());
    }

    private class TestDocumentProcessingTask extends DocumentProcessingTask {
        private TestDocumentProcessingTask(DocumentProtocol.Priority priority) {
            super(new TestRequestContext(priority), null, null);
        }
    }

    private class TestRequestContext implements RequestContext {
        private final DocumentProtocol.Priority priority;

        public TestRequestContext(DocumentProtocol.Priority priority) {
            this.priority = priority;
        }

        @Override
        public List<Processing> getProcessings() {
            return null;
        }

        @Override
        public void skip() {
        }

        @Override
        public void processingDone(List<Processing> processing) {
        }

        @Override
        public void processingFailed(ErrorCode error, String msg) {
        }

        @Override
        public void processingFailed(Exception exception) {
        }

        @Override
        public int getApproxSize() {
            return 0;
        }

        @Override
        public int getPriority() {
            return priority.getValue();
        }

        @Override
        public boolean isProcessable() {
            return true;
        }

        @Override
        public URI getUri() {
            return null;
        }

        @Override
        public String getServiceName() {
            return null;
        }
    }
}
