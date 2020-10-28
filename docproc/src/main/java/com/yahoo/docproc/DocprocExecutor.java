// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.jdisc.Metric;
import com.yahoo.statistics.Counter;
import com.yahoo.text.Utf8;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * An executor executed incoming processings on its CallStack
 *
 * @author Einar M R Rosenvinge
 */
public class DocprocExecutor {

    private final static String METRIC_NAME_DOCUMENTS_PROCESSED = "documents_processed";

    private static final Logger log = Logger.getLogger(DocprocExecutor.class.getName());

    private final String name;
    private final String docCounterName;
    private final Counter docCounter;
    private final Metric metric;
    private Function<String, Metric.Context> contexts;
    private final CallStack callStack;

    /**
     * Creates a new named DocprocExecutor with the given CallStack.
     *
     * @param name the name of this executor
     * @param callStack the chain of document processors this executor shall execute on processings
     */
    public DocprocExecutor(String name, CallStack callStack) {
        this.name = name;
        String chainDimension = name != null ? name.replaceAll("[^\\p{Alnum}]", "_") : name;
        docCounterName = "chain_" + chainDimension + "_documents";
        docCounter = new Counter(docCounterName, callStack.getStatistics(), false);
        this.metric = callStack.getMetric();
        this.callStack = callStack;
        this.callStack.setName(name);
        this.contexts = cachedContexts(chainDimension);
    }

    /**
     * Creates a new named DocprocExecutor, with the same instance variables as the given executor,
     * but a new call stack.
     *
     * @param oldExecutor the executor to inherit the instance variables from, sans call stack.
     * @param callStack the call stack to use.
     */
    public DocprocExecutor(DocprocExecutor oldExecutor, CallStack callStack) {
        this.name = oldExecutor.name;
        this.docCounterName = oldExecutor.docCounterName;
        this.docCounter = oldExecutor.docCounter;
        this.metric = oldExecutor.metric;
        this.contexts = oldExecutor.contexts;
        this.callStack = callStack;
    }

    public CallStack getCallStack() {
        return callStack;
    }

    public String getName() {
        return name;
    }

    private void incrementNumDocsProcessed(Processing processing) {
        List<DocumentOperation> operations = processing.getOnceOperationsToBeProcessed();
        if ( ! operations.isEmpty()) {
            docCounter.increment(operations.size());
            metric.add(docCounterName, operations.size(), null);
            operations.stream()
                      .collect(groupingBy(operation -> operation.getId().getDocType(), counting()))
                      .forEach((type, count) -> metric.add(METRIC_NAME_DOCUMENTS_PROCESSED, count, contexts.apply(type)));
        }
    }

    /**
     * Processes a given Processing through the CallStack of this executor.
     *
     * @param processing the Processing to process. The CallStack of the Processing will be set to a clone of the CallStack of this executor, iff. it is currently null.
     * @return a Progress; if this is LATER, the Processing is not done and must be reprocessed later.
     * @throws RuntimeException if a document processor throws an exception during processing.
     * @see com.yahoo.docproc.Processing
     */
    public DocumentProcessor.Progress process(Processing processing) {
        processing.setServiceName(getName());
        if (processing.callStack() == null) {
            processing.setCallStack(new CallStack(getCallStack()));
        }

        DocumentProcessor.Progress progress = DocumentProcessor.Progress.DONE;
        //metrics stuff:
        //TODO: Note that this is *wrong* in case of Progress.LATER, documents are then counted several times until the Processing is DONE or FAILED.
        incrementNumDocsProcessed(processing);
        do {
            Call call = processing.callStack().pop();
            if (call == null) {
                // No more processors - done
                return progress;
            }

            progress = DocumentProcessor.Progress.DONE;
            //might throw exception, which is OK:
            progress = call.call(processing);

            if (log.isLoggable(Level.FINEST)) {
                logProgress(processing, progress, call);
            }

            if (DocumentProcessor.Progress.LATER.equals(progress)) {
                processing.callStack().addNext(call);
                return progress;
            }
        } while (DocumentProcessor.Progress.DONE.equals(progress));
        return progress;
    }

    private void logProgress(Processing processing, DocumentProcessor.Progress progress, Call call) {
        StringBuilder message = new StringBuilder();
        boolean first = true;
        message.append(call.getDocumentProcessorId()).append(" of class ")
                .append(call.getDocumentProcessor().getClass().getSimpleName()).append(" returned ").append(progress)
                .append(" for the documents: [");
        for (DocumentOperation op : processing.getDocumentOperations()) {
            if (first) {
                first = false;
            } else {
                message.append(", ");
            }
            if (op instanceof DocumentPut) {
                message.append(Utf8.toString(JsonWriter.toByteArray(((DocumentPut) op).getDocument())));
            } else {
                message.append(op.toString());
            }
        }
        message.append("]");
        log.log(Level.FINEST, message.toString());
    }

    /**
     * Processes a given Processing through the CallStack of this executor. Note that if a DocumentProcessor
     * returns a LaterProgress for this processing, it will be re-processed (after waiting the specified delay given
     * by the LaterProgress), until done or failed.
     *
     * @param processing the Processing to process. The CallStack of the Processing will be set to a clone of the CallStack of this executor, iff. it is currently null.
     * @return a Progress; this is never a LaterProgress.
     * @throws RuntimeException if a document processor throws an exception during processing, or this thread is interrupted while waiting.
     * @see com.yahoo.docproc.Processing
     * @see com.yahoo.docproc.DocumentProcessor.Progress
     * @see com.yahoo.docproc.DocumentProcessor.LaterProgress
     */
    public DocumentProcessor.Progress processUntilDone(Processing processing) {
        DocumentProcessor.Progress progress;
        while (true) {
            progress = process(processing);
            if (!(progress instanceof DocumentProcessor.LaterProgress)) {
                break;
            }
            DocumentProcessor.LaterProgress later = (DocumentProcessor.LaterProgress) progress;
            try {
                Thread.sleep(later.getDelay());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
        return progress;
    }

    private Function<String, Metric.Context> cachedContexts(String chainDimension) {
        Map<String, Metric.Context> contextCache = new ConcurrentHashMap<>();
        return documentType -> contextCache.computeIfAbsent(documentType, type -> {
            Map<String, String> dimensions = new HashMap<>(2);
            dimensions.put("chain", chainDimension);
            dimensions.put("documenttype", type);
            return metric.createContext(dimensions);
        });
    }

}
