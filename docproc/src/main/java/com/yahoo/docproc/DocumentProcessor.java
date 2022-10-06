// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.collections.Pair;
import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.docproc.impl.DocprocService;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * <p>A document processor is a component which performs some
 * operation on a document or document update. Document processors are asynchronous,
 * they may request some data and then return. The processing framework
 * is responsible for calling processors again at unspecified times
 * until they are done processing the document or document update.</p>
 *
 * <p>Document processor instances
 * are chained together by the framework to realize a complete processing pipeline.
 * The processing chain is represented by the processor instances themselves, see
 * getNext/setNext. Document processors may optionally control the routing
 * through the chain by setting the next processor on ongoing processings.</p>
 * 
 * <p>A processing may contain one or multiple documents or document updates. Document processors
 * may optionally handle collections of processors in some other way than just
 * processing each one in order.</p>
 *
 * <p>A document processor <i>must</i> have an empty constructor. When instantiated
 * from Vespa config (as opposed to being instantiated programmatically in a stand-alone
 * Docproc system), the framework is responsible for configuring the processor using
  * setConfig(). If a document processor wants to do some initial setup after configuration
 * has been set, but before it has begun processing documents or document updates, it should
 * override initialize(). </p>
 * 
 * <p>Document processors must be thread safe. To ensure this, make sure that
 * access to any mutable, thread-unsafe state held in a field by the processor is
 * synchronized.</p>
 *
 * @author bratseth
 */
public abstract class DocumentProcessor extends ChainedComponent {

    static Logger log = Logger.getLogger(DocprocService.class.getName());

    /** Schema map for doctype-fieldnames */
    private Map<Pair<String,String>, String> fieldMap = new HashMap<>();

    /** For a doc type, the actual field name mapping to do */
    // TODO: How to flush this when reconfig of schemamapping?
    private final Map<String, Map<String, String>> docMapCache = new HashMap<>();

    private final boolean hasAnnotations;

    public DocumentProcessor() {
        hasAnnotations = getClass().getAnnotation(Accesses.class) != null;
    }

    final boolean hasAnnotations() {
        return hasAnnotations;
    }

    /**
     * Processes a processing, which can contain zero or more document bases. The implementing document processor
     * is free to modify, replace or delete elements in the list inside processing.
     *
     * @param processing the processing to process
     * @return the outcome of this processing
     */
    public abstract Progress process(Processing processing);

    /** Sets the schema map for field names */
    public void setFieldMap(Map<Pair<String, String>, String> fieldMap) {
        this.fieldMap = fieldMap;

    }

    /** Schema map for field names (doctype,from)→to */
    public Map<Pair<String, String>, String> getFieldMap() {
        return fieldMap;
    }

    public Map<String, String> getDocMap(String docType) {
        Map<String, String> cached = docMapCache.get(docType);
        if (cached!=null) {
            return cached;
        }
        Map<String, String> ret = new HashMap<>();
        for (Entry<Pair<String, String>, String> e : fieldMap.entrySet()) {
            // Remember to include tuple if doctype is unset in mapping
            if (docType.equals(e.getKey().getFirst()) || e.getKey().getFirst() == null || "".equals(e.getKey().getFirst())) {
                ret.put(e.getKey().getSecond(), e.getValue());
            }
        }
        docMapCache.put(docType, ret);
        return ret;
    }

    @Override
    public String toString() {
        return "processor " + getId().stringValue();
    }

    /** An enumeration of possible results of calling a process method */
    public static class Progress {

        /** Returned by a processor when it is done with a processing */
        public static final Progress DONE = new Progress("done");

        /**
         * Returned by a processor when it should be called again later
         * for the same processing
         */
        public static final Progress LATER = new LaterProgress();

        /**
         * Returned by a processor when a processing has failed
         * and it should not be called again for this processing.
         */
        public static final Progress FAILED = new Progress("failed");

        /**
         * Returned by a processor when processing has permanently failed,
         * so that the document processing service should disable itself until
         * reconfigured or restarted.
         */
        public static final Progress PERMANENT_FAILURE = new Progress("permanent_failure");

        private final String name;

        private Optional<String> reason = Optional.empty();

        protected Progress(String name) {
            this.name = name;
        }

        protected Progress(String name, String reason) {
            this(name);
            this.reason = Optional.of(reason);
        }

        public static Progress later(long delay) {
            return new LaterProgress(delay);
        }

        public Progress withReason(String reason) {
            return new Progress(this.name, reason);
        }

        @Override
        public String toString() {
            return name;
        }

        public Optional<String> getReason() {
            return reason;
        }

        @Override
        public boolean equals(Object object) {
            return object instanceof Progress && ((Progress) object).name.equals(this.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

    }

    public static final class LaterProgress extends Progress {

        private final long delay;
        public static final long DEFAULT_LATER_DELAY = 20;  //ms

        private LaterProgress() {
            this(DEFAULT_LATER_DELAY);
        }

        private LaterProgress(long delay) {
            super("later");
            this.delay = delay;
        }

        public long getDelay() {
            return delay;
        }

    }

}
