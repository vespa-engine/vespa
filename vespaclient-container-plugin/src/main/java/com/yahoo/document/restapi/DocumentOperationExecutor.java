// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.FixedBucketSpaces;
import com.yahoo.document.fieldset.AllFields;
import com.yahoo.documentapi.DocumentOperationParameters;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.text.Text;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Wraps the document API with an executor that can retry and time out document operations,
 * as well as compute the required visitor parameters for visitor sessions.
 *
 * @author jonmv
 */
public interface DocumentOperationExecutor {

    default void shutdown() { }

    void get(DocumentId id, DocumentOperationParameters parameters, OperationContext context);

    void put(DocumentPut put, DocumentOperationParameters parameters, OperationContext context);

    void update(DocumentUpdate update, DocumentOperationParameters parameters, OperationContext context);

    void remove(DocumentId id, DocumentOperationParameters parameters, OperationContext context);

    void visit(VisitorOptions options, VisitOperationsContext context);

    String routeToCluster(String cluster);

    enum ErrorType {
        OVERLOAD,
        NOT_FOUND,
        PRECONDITION_FAILED,
        BAD_REQUEST,
        TIMEOUT,
        ERROR;
    }


    /** The executor will call <em>exactly one</em> callback <em>exactly once</em> for contexts submitted to it. */
    class Context<T> {

        private final AtomicBoolean handled = new AtomicBoolean();
        private final BiConsumer<ErrorType, String> onError;
        private final Consumer<T> onSuccess;

        Context(BiConsumer<ErrorType, String> onError, Consumer<T> onSuccess) {
            this.onError = onError;
            this.onSuccess = onSuccess;
        }

        public void error(ErrorType type, String message) {
            if ( ! handled.getAndSet(true))
                onError.accept(type, message);
        }

        public void success(T result) {
            if ( ! handled.getAndSet(true))
                onSuccess.accept(result);
        }

        public boolean handled() {
            return handled.get();
        }

    }


    /** Context for reacting to the progress of a visitor session. Completion signalled by an optional progress token. */
    class VisitOperationsContext extends Context<Optional<String>> {

        private final Consumer<Document> onDocument;

        public VisitOperationsContext(BiConsumer<ErrorType, String> onError, Consumer<Optional<String>> onSuccess, Consumer<Document> onDocument) {
            super(onError, onSuccess);
            this.onDocument = onDocument;
        }

        public void document(Document document) {
            if ( ! handled())
                onDocument.accept(document);
        }

    }


    /** Context for a document operation. */
    class OperationContext extends Context<Optional<Document>> {

        public OperationContext(BiConsumer<ErrorType, String> onError, Consumer<Optional<Document>> onSuccess) {
            super(onError, onSuccess);
        }

    }


    class VisitorOptions {

        final Optional<String> cluster;
        final Optional<String> namespace;
        final Optional<String> documentType;
        final Optional<Group> group;
        final Optional<String> selection;
        final Optional<String> fieldSet;
        final Optional<String> continuation;
        final Optional<String> bucketSpace;
        final Optional<Integer> wantedDocumentCount;
        final Optional<Integer> concurrency;

        private VisitorOptions(Optional<String> cluster, Optional<String> documentType, Optional<String> namespace,
                               Optional<Group> group, Optional<String> selection, Optional<String> fieldSet,
                               Optional<String> continuation, Optional<String> bucketSpace,
                               Optional<Integer> wantedDocumentCount, Optional<Integer> concurrency) {
            this.cluster = cluster;
            this.namespace = namespace;
            this.documentType = documentType;
            this.group = group;
            this.selection = selection;
            this.fieldSet = fieldSet;
            this.continuation = continuation;
            this.bucketSpace = bucketSpace;
            this.wantedDocumentCount = wantedDocumentCount;
            this.concurrency = concurrency;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VisitorOptions that = (VisitorOptions) o;
            return cluster.equals(that.cluster) &&
                   namespace.equals(that.namespace) &&
                   documentType.equals(that.documentType) &&
                   group.equals(that.group) &&
                   selection.equals(that.selection) &&
                   fieldSet.equals(that.fieldSet) &&
                   continuation.equals(that.continuation) &&
                   bucketSpace.equals(that.bucketSpace) &&
                   wantedDocumentCount.equals(that.wantedDocumentCount) &&
                   concurrency.equals(that.concurrency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(cluster, namespace, documentType, group, selection, fieldSet, continuation, bucketSpace, wantedDocumentCount, concurrency);
        }

        @Override
        public String toString() {
            return "VisitorOptions{" +
                   "cluster=" + cluster +
                   ", namespace=" + namespace +
                   ", documentType=" + documentType +
                   ", group=" + group +
                   ", selection=" + selection +
                   ", fieldSet=" + fieldSet +
                   ", continuation=" + continuation +
                   ", bucketSpace=" + bucketSpace +
                   ", wantedDocumentCount=" + wantedDocumentCount +
                   ", concurrency=" + concurrency +
                   '}';
        }

        public static Builder builder() { return new Builder(); }


        public static class Builder {

            private String cluster;
            private String documentType;
            private String namespace;
            private Group group;
            private String selection;
            private String fieldSet;
            private String continuation;
            private String bucketSpace;
            private Integer wantedDocumentCount;
            private Integer concurrency;

            public Builder cluster(String cluster) {
                this.cluster = cluster;
                return this;
            }

            public Builder documentType(String documentType) {
                this.documentType = documentType;
                return this;
            }

            public Builder namespace(String namespace) {
                this.namespace = namespace;
                return this;
            }

            public Builder group(Group group) {
                this.group = group;
                return this;
            }

            public Builder selection(String selection) {
                this.selection = selection;
                return this;
            }

            public Builder fieldSet(String fieldSet) {
                this.fieldSet = fieldSet;
                return this;
            }

            public Builder continuation(String continuation) {
                this.continuation = continuation;
                return this;
            }

            public Builder bucketSpace(String bucketSpace) {
                this.bucketSpace = bucketSpace;
                return this;
            }

            public Builder wantedDocumentCount(Integer wantedDocumentCount) {
                this.wantedDocumentCount = wantedDocumentCount;
                return this;
            }

            public Builder concurrency(Integer concurrency) {
                this.concurrency = concurrency;
                return this;
            }

            public VisitorOptions build() {
                return new VisitorOptions(Optional.ofNullable(cluster), Optional.ofNullable(documentType),
                                          Optional.ofNullable(namespace), Optional.ofNullable(group),
                                          Optional.ofNullable(selection), Optional.ofNullable(fieldSet),
                                          Optional.ofNullable(continuation), Optional.ofNullable(bucketSpace),
                                          Optional.ofNullable(wantedDocumentCount), Optional.ofNullable(concurrency));
            }

        }

    }


    class Group {

        private final String value;
        private final String docIdPart;
        private final String selection;

        private Group(String value, String docIdPart, String selection) {
            Text.validateTextString(value)
                .ifPresent(codePoint -> { throw new IllegalArgumentException(String.format("Illegal code point U%04X in group", codePoint)); });
            this.value = value;
            this.docIdPart = docIdPart;
            this.selection = selection;
        }

        public static Group of(long value) { return new Group(Long.toString(value), "n=" + value, "id.user==" + value); }
        public static Group of(String value) { return new Group(value, "g=" + value, "id.group=='" + value.replaceAll("'", "\\'") + "'"); }

        public String value() { return value; }
        public String docIdPart() { return docIdPart; }
        public String selection() { return selection; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Group group = (Group) o;
            return value.equals(group.value) &&
                   docIdPart.equals(group.docIdPart) &&
                   selection.equals(group.selection);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, docIdPart, selection);
        }

        @Override
        public String toString() {
            return "Group{" +
                   "value='" + value + '\'' +
                   ", docIdPart='" + docIdPart + '\'' +
                   ", selection='" + selection + '\'' +
                   '}';
        }

    }

}
