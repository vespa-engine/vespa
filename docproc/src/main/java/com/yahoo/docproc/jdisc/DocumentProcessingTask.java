// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.collections.Tuple2;
import com.yahoo.docproc.Call;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.impl.DocprocExecutor;
import com.yahoo.docproc.impl.DocprocService;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.impl.HandledProcessingException;
import com.yahoo.docproc.Processing;
import java.util.logging.Level;
import com.yahoo.yolean.Exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
public class DocumentProcessingTask implements Runnable {

    private static Logger log = Logger.getLogger(DocumentProcessingTask.class.getName());
    private final List<Processing> processings = new ArrayList<>();
    private final List<Processing> processingsDone = new ArrayList<>();

    private final DocumentProcessingHandler docprocHandler;
    private final RequestContext requestContext;

    private final DocprocService service;
    private final ThreadPoolExecutor executor;

    public DocumentProcessingTask(RequestContext requestContext, DocumentProcessingHandler docprocHandler,
                                  DocprocService service, ThreadPoolExecutor executor) {
        this.requestContext = requestContext;
        this.docprocHandler = docprocHandler;
        this.service = service;
        this.executor = executor;
    }

    void submit() {
        try {
            executor.execute(this);
        } catch (RejectedExecutionException ree) {
            queueFull();
        }
    }

    @Override
    public void run() {
        try {
            try {
                processings.addAll(requestContext.getProcessings());
            } catch (Exception e) {
                //deserialization failed:
                log.log(Level.WARNING, "Deserialization of message failed.", e);
                requestContext.processingFailed(e);
                return;
            }

            DocprocExecutor executor = service.getExecutor();
            DocumentProcessor.Progress progress = process(executor);

            if (DocumentProcessor.Progress.LATER.equals(progress) && !processings.isEmpty()) {
                DocumentProcessor.LaterProgress laterProgress = (DocumentProcessor.LaterProgress) progress;
                docprocHandler.submit(this, laterProgress.getDelay());
            }
        } catch (Error error) {
            try {
                log.log(Level.SEVERE, Exceptions.toMessageString(error), error);
            } catch (Throwable t) {
                // do nothing
            } finally {
                Runtime.getRuntime().halt(1);
            }
        }
    }

    /**
     * Processes a single Processing, and fails the message if this processing fails.
     *
     * @param executor the DocprocService to use for processing
     */
    private DocumentProcessor.Progress process(DocprocExecutor executor) {
        Iterator<Processing> iterator = processings.iterator();
        List<Tuple2<DocumentProcessor.Progress, Processing>> later = new ArrayList<>();
        while (iterator.hasNext()) {
            Processing processing = iterator.next();
            iterator.remove();
            if (requestContext.hasExpired()) {
                DocumentProcessor.Progress progress = DocumentProcessor.Progress.FAILED;
                final String location;
                if (processing != null) {
                    final CallStack callStack = processing.callStack();
                    if (callStack != null) {
                        final Call lastPopped = callStack.getLastPopped();
                        if (lastPopped != null) {
                            location = lastPopped.toString();
                        } else {
                            location = "empty call stack or no processors popped";
                        }
                    } else {
                        location = "no call stack";
                    }
                } else {
                    location = "no processing instance";
                }
                log.log(Level.FINE, () -> "Time is up for '" + processing + " failed, " + location + "'.");
                requestContext.processingFailed(RequestContext.ErrorCode.ERROR_PROCESSING_FAILURE, "Time is up.");
                return progress;
            }

            DocumentProcessor.Progress progress = DocumentProcessor.Progress.FAILED;
            try {
                progress = executor.process(processing);
            } catch (Exception e) {
                logProcessingFailure(processing, e);
                requestContext.processingFailed(e);
                return progress;
            }

            if (DocumentProcessor.Progress.LATER.equals(progress)) {
                later.add(new Tuple2<>(progress, processing));
            } else if (DocumentProcessor.Progress.DONE.equals(progress)) {
                processingsDone.add(processing);
            } else if (DocumentProcessor.Progress.FAILED.equals(progress)) {
                logProcessingFailure(processing, null);
                requestContext.processingFailed(RequestContext.ErrorCode.ERROR_PROCESSING_FAILURE,
                        progress.getReason().orElse("Document processing failed."));
                return progress;
            } else if (DocumentProcessor.Progress.PERMANENT_FAILURE.equals(progress)) {
                logProcessingFailure(processing, null);
                requestContext.processingFailed(RequestContext.ErrorCode.ERROR_PROCESSING_FAILURE,
                        progress.getReason().orElse("Document processing failed."));
                return progress;
            }
        }

        // Processings that have FAILED will have made this method terminate by now.
        // We now have successful Processings in 'processingsDone' and
        // the ones that have returned LATER in 'later'.

        if (!later.isEmpty()) {
            // Outdated comment:
            // "if this was a multioperationmessage and more than one of the processings returned LATER,
            // return the one with the lowest timeout:"
            // As multioperation is removed this can probably be simplified?
            DocumentProcessor.LaterProgress shortestDelay = (DocumentProcessor.LaterProgress) later.get(0).first;
            for (Tuple2<DocumentProcessor.Progress, Processing> tuple : later) {
                // re-add the LATER one to processings
                processings.add(tuple.second);
                // check to see if this one had a lower timeout than the previous one:
                if (((DocumentProcessor.LaterProgress) tuple.first).getDelay() < shortestDelay.getDelay()) {
                    shortestDelay = (DocumentProcessor.LaterProgress) tuple.first;
                }
            }
            return shortestDelay;
        } else {
            requestContext.processingDone(processingsDone);
            return DocumentProcessor.Progress.DONE;
        }
    }


    void queueFull() {
        requestContext.processingFailed(RequestContext.ErrorCode.ERROR_BUSY,
                                        "Queue temporarily full. Returning message " + requestContext +
                                        ". Will be automatically resent.");
    }

    @Override
    public String toString() {
        return "ProcessingTask{" +
               "processings=" + processings +
               ", processingsDone=" + processingsDone +
               ", requestContext=" + requestContext +
               '}';
    }

    private static void logProcessingFailure(Processing processing, Exception exception) {
        //LOGGING ONLY:
        String errorMsg = processing + " failed at " + processing.callStack().getLastPopped();
        if (exception != null) {
            if (exception instanceof HandledProcessingException) {
                errorMsg += ". Error message: " + exception.getMessage();
                log.log(Level.WARNING, errorMsg);
                log.log(Level.FINE, "Chained exception:", exception);
            } else {
                log.log(Level.WARNING, errorMsg, exception);
            }
        } else {
            log.log(Level.WARNING, errorMsg);
        }
        //LOGGING OF STACK TRACE:
        if (exception != null) {
            StringWriter backtrace = new StringWriter();
            exception.printStackTrace(new PrintWriter(backtrace));
            log.log(Level.FINE, () -> "Failed to process " + processing + ": " + backtrace);
        }
    }

}
