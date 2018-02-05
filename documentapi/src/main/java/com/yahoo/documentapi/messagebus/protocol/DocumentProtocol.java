// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.google.common.annotations.Beta;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.component.VersionSpecification;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentTypeManagerConfigurer;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.documentapi.metrics.DocumentProtocolMetricSet;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Routable;
import com.yahoo.messagebus.metrics.MetricSet;
import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingNodeIterator;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.text.Utf8String;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Implements the message bus protocol that is used by all components of Vespa.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class DocumentProtocol implements Protocol {

    private static final Logger log = Logger.getLogger(DocumentProtocol.class.getName());
    private final DocumentProtocolMetricSet metrics = new DocumentProtocolMetricSet();
    private final RoutingPolicyRepository routingPolicyRepository = new RoutingPolicyRepository(metrics);
    private final RoutableRepository routableRepository;
    private final DocumentTypeManager docMan;

    /**
     * The name of this protocol.
     */
    public static final Utf8String NAME = new Utf8String("document");

    /**
     * All message types that are implemented by this protocol.
     */
    public static final int DOCUMENT_MESSAGE = 100000;
    public static final int MESSAGE_GETDOCUMENT = DOCUMENT_MESSAGE + 3;
    public static final int MESSAGE_PUTDOCUMENT = DOCUMENT_MESSAGE + 4;
    public static final int MESSAGE_REMOVEDOCUMENT = DOCUMENT_MESSAGE + 5;
    public static final int MESSAGE_UPDATEDOCUMENT = DOCUMENT_MESSAGE + 6;
    public static final int MESSAGE_CREATEVISITOR = DOCUMENT_MESSAGE + 7;
    public static final int MESSAGE_DESTROYVISITOR = DOCUMENT_MESSAGE + 8;
    public static final int MESSAGE_VISITORINFO = DOCUMENT_MESSAGE + 9;
    public static final int MESSAGE_SEARCHRESULT = DOCUMENT_MESSAGE + 11;
    public static final int MESSAGE_DOCUMENTSUMMARY = DOCUMENT_MESSAGE + 14;
    public static final int MESSAGE_MAPVISITOR = DOCUMENT_MESSAGE + 15;
    public static final int MESSAGE_GETBUCKETSTATE = DOCUMENT_MESSAGE + 18;
    public static final int MESSAGE_STATBUCKET = DOCUMENT_MESSAGE + 19;
    public static final int MESSAGE_GETBUCKETLIST = DOCUMENT_MESSAGE + 20;
    public static final int MESSAGE_DOCUMENTLIST = DOCUMENT_MESSAGE + 21;
    public static final int MESSAGE_EMPTYBUCKETS = DOCUMENT_MESSAGE + 23;
    public static final int MESSAGE_REMOVELOCATION = DOCUMENT_MESSAGE + 24;
    public static final int MESSAGE_QUERYRESULT = DOCUMENT_MESSAGE + 25;
    public static final int MESSAGE_BATCHDOCUMENTUPDATE = DOCUMENT_MESSAGE + 26;

    /**
     * All reply types that are implemented by this protocol.
     */
    public static final int DOCUMENT_REPLY = 200000;
    public static final int REPLY_GETDOCUMENT = DOCUMENT_REPLY + 3;
    public static final int REPLY_PUTDOCUMENT = DOCUMENT_REPLY + 4;
    public static final int REPLY_REMOVEDOCUMENT = DOCUMENT_REPLY + 5;
    public static final int REPLY_UPDATEDOCUMENT = DOCUMENT_REPLY + 6;
    public static final int REPLY_CREATEVISITOR = DOCUMENT_REPLY + 7;
    public static final int REPLY_DESTROYVISITOR = DOCUMENT_REPLY + 8;
    public static final int REPLY_VISITORINFO = DOCUMENT_REPLY + 9;
    public static final int REPLY_SEARCHRESULT = DOCUMENT_REPLY + 11;
    public static final int REPLY_DOCUMENTSUMMARY = DOCUMENT_REPLY + 14;
    public static final int REPLY_MAPVISITOR = DOCUMENT_REPLY + 15;
    public static final int REPLY_GETBUCKETSTATE = DOCUMENT_REPLY + 18;
    public static final int REPLY_STATBUCKET = DOCUMENT_REPLY + 19;
    public static final int REPLY_GETBUCKETLIST = DOCUMENT_REPLY + 20;
    public static final int REPLY_DOCUMENTLIST = DOCUMENT_REPLY + 21;
    public static final int REPLY_EMPTYBUCKETS = DOCUMENT_REPLY + 23;
    public static final int REPLY_REMOVELOCATION = DOCUMENT_REPLY + 24;
    public static final int REPLY_QUERYRESULT = DOCUMENT_REPLY + 25;
    public static final int REPLY_BATCHDOCUMENTUPDATE = DOCUMENT_REPLY + 26;
    public static final int REPLY_WRONGDISTRIBUTION = DOCUMENT_REPLY + 1000;
    public static final int REPLY_DOCUMENTIGNORED = DOCUMENT_REPLY + 1001;

    /**
     * Important note on adding new error codes to the Document protocol:
     *
     * Changes to this protocol must be reflected in both the Java and C++ versions
     * of the code. Furthermore, ErrorCodesTest must be updated across both languages
     * to include the new error code. Otherwise, cross-language correctness may no
     * longer be guaranteed.
     */

    /**
     * Used by policies to  indicate an inappropriate message.
     */
    public static final int ERROR_MESSAGE_IGNORED = ErrorCode.APP_FATAL_ERROR + 1;
    /**
     * Used for error policy when policy creation failed.
     */
    public static final int ERROR_POLICY_FAILURE = ErrorCode.APP_FATAL_ERROR + 2;
    /**
     * Document in operation cannot be found. (VDS Get and Remove)
     */
    public static final int ERROR_DOCUMENT_NOT_FOUND = ErrorCode.APP_FATAL_ERROR + 1001;
    /**
     * Operation cannot be performed because token already exist. (Create bucket, create visitor)
     */
    public static final int ERROR_DOCUMENT_EXISTS = ErrorCode.APP_FATAL_ERROR + 1002;
    /**
     * Node have not implemented support for the given operation.
     */
    public static final int ERROR_NOT_IMPLEMENTED = ErrorCode.APP_FATAL_ERROR + 1004;
    /**
     * Parameters given in request is illegal.
     */
    public static final int ERROR_ILLEGAL_PARAMETERS = ErrorCode.APP_FATAL_ERROR + 1005;
    /**
     * Unknown request received. (New client requesting from old server)
     */
    public static final int ERROR_UNKNOWN_COMMAND = ErrorCode.APP_FATAL_ERROR + 1007;
    /**
     * Request cannot be decoded.
     */
    public static final int ERROR_UNPARSEABLE = ErrorCode.APP_FATAL_ERROR + 1008;
    /**
     * Not enough free space on disk to perform operation.
     */
    public static final int ERROR_NO_SPACE = ErrorCode.APP_FATAL_ERROR + 1009;
    /**
     * Request was not handled correctly.
     */
    public static final int ERROR_IGNORED = ErrorCode.APP_FATAL_ERROR + 1010;
    /**
     * We failed in some way we didn't expect to fail.
     */
    public static final int ERROR_INTERNAL_FAILURE = ErrorCode.APP_FATAL_ERROR + 1011;
    /**
     * Node refuse to perform operation. (Illegally formed message?)
     */
    public static final int ERROR_REJECTED = ErrorCode.APP_FATAL_ERROR + 1012;
    /**
     * Test and set condition (selection) failed.
     */
    @Beta
    public static final int ERROR_TEST_AND_SET_CONDITION_FAILED = ErrorCode.APP_FATAL_ERROR + 1013;

    /**
     * Failed to process the given request. (Used by docproc)
     */
    public static final int ERROR_PROCESSING_FAILURE = ErrorCode.APP_FATAL_ERROR + 2001;
    /** Unique timestamp specified for new operation is already in use. */
    public static final int ERROR_TIMESTAMP_EXIST    = ErrorCode.APP_FATAL_ERROR + 2002;
    /**
     * Node not ready to perform operation. (Initializing VDS nodes)
     */
    public static final int ERROR_NODE_NOT_READY = ErrorCode.APP_TRANSIENT_ERROR + 1001;
    /**
     * Wrong node to talk to in current state. (VDS system state disagreement)
     */
    public static final int ERROR_WRONG_DISTRIBUTION = ErrorCode.APP_TRANSIENT_ERROR + 1002;
    /**
     * Operation cut short and aborted. (Destroy visitor, node stopping)
     */
    public static final int ERROR_ABORTED = ErrorCode.APP_TRANSIENT_ERROR + 1004;
    /**
     * Node too busy to process request (Typically full queues)
     */
    public static final int ERROR_BUSY = ErrorCode.APP_TRANSIENT_ERROR + 1005;
    /**
     * Lost connection with the node we requested something from.
     */
    public static final int ERROR_NOT_CONNECTED = ErrorCode.APP_TRANSIENT_ERROR + 1006;
    /**
     * We failed accessing the disk, which we think is a disk hardware problem.
     */
    public static final int ERROR_DISK_FAILURE = ErrorCode.APP_TRANSIENT_ERROR + 1007;
    /**
     * We failed during an IO operation, we dont think is a specific disk hardware problem.
     */
    public static final int ERROR_IO_FAILURE = ErrorCode.APP_TRANSIENT_ERROR + 1008;
    /**
     * Bucket given in operation not found due to bucket database
     * inconsistencies between storage and distributor nodes.
     */
    public static final int ERROR_BUCKET_NOT_FOUND = ErrorCode.APP_TRANSIENT_ERROR + 1009;
    /**
     * Bucket recently removed, such that operation cannot be performed.
     * Differs from BUCKET_NOT_FOUND in that there is no db inconsistency.
     */
    public static final int ERROR_BUCKET_DELETED = ErrorCode.APP_TRANSIENT_ERROR + 1012;
    /**
     * Storage node received a timestamp that is stale. Likely clock skew.
     */
    public static final int ERROR_STALE_TIMESTAMP = ErrorCode.APP_TRANSIENT_ERROR + 1013;

    /**
     * The given node have gotten a critical error and have suspended itself.
     */
    public static final int ERROR_SUSPENDED = ErrorCode.APP_TRANSIENT_ERROR + 2001;

    /**
     * <p>Define the different priorities allowed for document api messages. Most user traffic should be fit into the
     * NORMAL categories. Traffic in the HIGH end will be usually be prioritized over important maintenance operations.
     * Traffic in the LOW end will be prioritized after these operations.</p>
     */
    public enum Priority {
        HIGHEST(0),
        VERY_HIGH(1),
        HIGH_1(2),
        HIGH_2(3),
        HIGH_3(4),
        NORMAL_1(5),
        NORMAL_2(6),
        NORMAL_3(7),
        NORMAL_4(8),
        NORMAL_5(9),
        NORMAL_6(10),
        LOW_1(11),
        LOW_2(12),
        LOW_3(13),
        VERY_LOW(14),
        LOWEST(15);

        private final int val;

        private Priority(int val) {
            this.val = val;
        }

        public int getValue() {
            return val;
        }
    }

    /**
     * Get a priority enum instance by its value.
     *
     * @param val The value of the priority to return.
     * @return The priority enum instance.
     * @throws IllegalArgumentException If priority value is unknown.
     */
    public static Priority getPriority(int val) {
        for (Priority pri : Priority.values()) {
            if (val == pri.val) {
                return pri;
            }
        }
        throw new IllegalArgumentException("Unknown priority: " + val);
    }

    /**
     * Get priority enum instance by its name.
     *
     * @param name Name of priority.
     * @return Priority enum instance, given that <code>name</code> is valid.
     * @throws IllegalArgumentException If priority name is unknown.
     */
    public static Priority getPriorityByName(String name) {
        return Priority.valueOf(name);
    }

    public DocumentProtocol(DocumentTypeManager docMan) {
        this(docMan, null, new LoadTypeSet());
    }

    public DocumentProtocol(DocumentTypeManager docMan, String configId) {
        this(docMan, configId, new LoadTypeSet());
    }

    public DocumentProtocol(DocumentTypeManager docMan, String configId, LoadTypeSet set) {
        // Prepare config string for routing policy factories.
        String cfg = (configId == null ? "client" : configId);
        if (docMan != null) {
            this.docMan = docMan;
        } else {
            this.docMan = new DocumentTypeManager();
            DocumentTypeManagerConfigurer.configure(this.docMan, cfg);
        }
        routableRepository = new RoutableRepository(set);

        // When adding factories to this list, please KEEP THEM ORDERED alphabetically like they are now.
        putRoutingPolicyFactory("AND", new RoutingPolicyFactories.AndPolicyFactory());
        putRoutingPolicyFactory("Content", new RoutingPolicyFactories.ContentPolicyFactory());
        putRoutingPolicyFactory("DocumentRouteSelector", new RoutingPolicyFactories.DocumentRouteSelectorPolicyFactory(cfg));
        putRoutingPolicyFactory("Extern", new RoutingPolicyFactories.ExternPolicyFactory());
        putRoutingPolicyFactory("LocalService", new RoutingPolicyFactories.LocalServicePolicyFactory());
        putRoutingPolicyFactory("MessageType", new RoutingPolicyFactories.MessageTypePolicyFactory(cfg));
        putRoutingPolicyFactory("RoundRobin", new RoutingPolicyFactories.RoundRobinPolicyFactory());
        putRoutingPolicyFactory("LoadBalancer", new RoutingPolicyFactories.LoadBalancerPolicyFactory());
        putRoutingPolicyFactory("Storage", new RoutingPolicyFactories.StoragePolicyFactory());
        putRoutingPolicyFactory("SubsetService", new RoutingPolicyFactories.SubsetServicePolicyFactory());

        // Prepare version specifications to use when adding routable factories.
        VersionSpecification version50 = new VersionSpecification(5, 0);
        VersionSpecification version51 = new VersionSpecification(5, 1);
        VersionSpecification version52 = new VersionSpecification(5, 115);
        VersionSpecification version6 = new VersionSpecification(6, 999); // TODO change once stable protocol

        List<VersionSpecification> from50 = Arrays.asList(version50, version51, version52, version6);
        List<VersionSpecification> from51 = Arrays.asList(version51, version52, version6);
        List<VersionSpecification> from52 = Arrays.asList(version52, version6);
        List<VersionSpecification> from6 = Collections.singletonList(version6); // TODO decide minor version...

        // 5.0 serialization (keep alphabetized please)
        putRoutableFactory(MESSAGE_BATCHDOCUMENTUPDATE, new RoutableFactories50.BatchDocumentUpdateMessageFactory(), from50);
        putRoutableFactory(MESSAGE_CREATEVISITOR, new RoutableFactories50.CreateVisitorMessageFactory(), from50);
        putRoutableFactory(MESSAGE_DESTROYVISITOR, new RoutableFactories50.DestroyVisitorMessageFactory(), from50);
        putRoutableFactory(MESSAGE_DOCUMENTLIST, new RoutableFactories50.DocumentListMessageFactory(), from50);
        putRoutableFactory(MESSAGE_DOCUMENTSUMMARY, new RoutableFactories50.DocumentSummaryMessageFactory(), from50);
        putRoutableFactory(MESSAGE_EMPTYBUCKETS, new RoutableFactories50.EmptyBucketsMessageFactory(), from50);
        putRoutableFactory(MESSAGE_GETBUCKETLIST, new RoutableFactories50.GetBucketListMessageFactory(), from50);
        putRoutableFactory(MESSAGE_GETBUCKETSTATE, new RoutableFactories50.GetBucketStateMessageFactory(), from50);
        putRoutableFactory(MESSAGE_GETDOCUMENT, new RoutableFactories50.GetDocumentMessageFactory(), from50);
        putRoutableFactory(MESSAGE_MAPVISITOR, new RoutableFactories50.MapVisitorMessageFactory(), from50);
        putRoutableFactory(MESSAGE_PUTDOCUMENT, new RoutableFactories50.PutDocumentMessageFactory(), from50);
        putRoutableFactory(MESSAGE_QUERYRESULT, new RoutableFactories50.QueryResultMessageFactory(), from50);
        putRoutableFactory(MESSAGE_REMOVEDOCUMENT, new RoutableFactories50.RemoveDocumentMessageFactory(), from50);
        putRoutableFactory(MESSAGE_REMOVELOCATION, new RoutableFactories50.RemoveLocationMessageFactory(), from50);
        putRoutableFactory(MESSAGE_SEARCHRESULT, new RoutableFactories50.SearchResultMessageFactory(), from50);
        putRoutableFactory(MESSAGE_STATBUCKET, new RoutableFactories50.StatBucketMessageFactory(), from50);
        putRoutableFactory(MESSAGE_UPDATEDOCUMENT, new RoutableFactories50.UpdateDocumentMessageFactory(), from50);
        putRoutableFactory(MESSAGE_VISITORINFO, new RoutableFactories50.VisitorInfoMessageFactory(), from50);
        putRoutableFactory(REPLY_BATCHDOCUMENTUPDATE, new RoutableFactories50.BatchDocumentUpdateReplyFactory(), from50);
        putRoutableFactory(REPLY_CREATEVISITOR, new RoutableFactories50.CreateVisitorReplyFactory(), from50);
        putRoutableFactory(REPLY_DESTROYVISITOR, new RoutableFactories50.DestroyVisitorReplyFactory(), from50);
        putRoutableFactory(REPLY_DOCUMENTLIST, new RoutableFactories50.DocumentListReplyFactory(), from50);
        putRoutableFactory(REPLY_DOCUMENTSUMMARY, new RoutableFactories50.DocumentSummaryReplyFactory(), from50);
        putRoutableFactory(REPLY_EMPTYBUCKETS, new RoutableFactories50.EmptyBucketsReplyFactory(), from50);
        putRoutableFactory(REPLY_GETBUCKETLIST, new RoutableFactories50.GetBucketListReplyFactory(), from50);
        putRoutableFactory(REPLY_GETBUCKETSTATE, new RoutableFactories50.GetBucketStateReplyFactory(), from50);
        putRoutableFactory(REPLY_GETDOCUMENT, new RoutableFactories50.GetDocumentReplyFactory(), from50);
        putRoutableFactory(REPLY_MAPVISITOR, new RoutableFactories50.MapVisitorReplyFactory(), from50);
        putRoutableFactory(REPLY_PUTDOCUMENT, new RoutableFactories50.PutDocumentReplyFactory(), from50);
        putRoutableFactory(REPLY_QUERYRESULT, new RoutableFactories50.QueryResultReplyFactory(), from50);
        putRoutableFactory(REPLY_REMOVEDOCUMENT, new RoutableFactories50.RemoveDocumentReplyFactory(), from50);
        putRoutableFactory(REPLY_REMOVELOCATION, new RoutableFactories50.RemoveLocationReplyFactory(), from50);
        putRoutableFactory(REPLY_SEARCHRESULT, new RoutableFactories50.SearchResultReplyFactory(), from50);
        putRoutableFactory(REPLY_STATBUCKET, new RoutableFactories50.StatBucketReplyFactory(), from50);
        putRoutableFactory(REPLY_UPDATEDOCUMENT, new RoutableFactories50.UpdateDocumentReplyFactory(), from50);
        putRoutableFactory(REPLY_UPDATEDOCUMENT, new RoutableFactories50.UpdateDocumentReplyFactory(), from50);
        putRoutableFactory(REPLY_VISITORINFO, new RoutableFactories50.VisitorInfoReplyFactory(), from50);
        putRoutableFactory(REPLY_WRONGDISTRIBUTION, new RoutableFactories50.WrongDistributionReplyFactory(), from50);

        // 5.1 serialization
        putRoutableFactory(MESSAGE_CREATEVISITOR, new RoutableFactories51.CreateVisitorMessageFactory(), from51);
        putRoutableFactory(MESSAGE_GETDOCUMENT, new RoutableFactories51.GetDocumentMessageFactory(), from51);
        putRoutableFactory(REPLY_DOCUMENTIGNORED, new RoutableFactories51.DocumentIgnoredReplyFactory(), from51);

        // 5.2 serialization
        putRoutableFactory(MESSAGE_PUTDOCUMENT, new RoutableFactories52.PutDocumentMessageFactory(), from52);
        putRoutableFactory(MESSAGE_UPDATEDOCUMENT, new RoutableFactories52.UpdateDocumentMessageFactory(), from52);
        putRoutableFactory(MESSAGE_REMOVEDOCUMENT, new RoutableFactories52.RemoveDocumentMessageFactory(), from52);

        // 6.x serialization
        putRoutableFactory(MESSAGE_CREATEVISITOR, new RoutableFactories60.CreateVisitorMessageFactory(), from6);
        putRoutableFactory(MESSAGE_STATBUCKET, new RoutableFactories60.StatBucketMessageFactory(), from6);
        putRoutableFactory(MESSAGE_GETBUCKETLIST, new RoutableFactories60.GetBucketListMessageFactory(), from6);
    }

    /**
     * Adds a new routable factory to this protocol. This method is thread-safe, and may be invoked on a protocol object
     * that is already in use by a message bus instance. Notice that the name you supply for a factory is the
     * case-sensitive name that will be referenced by routes.
     *
     * @param name    The name of the factory to add.
     * @param factory The factory to add.
     * @return This, to allow chaining.
     */
    public DocumentProtocol putRoutingPolicyFactory(String name, RoutingPolicyFactory factory) {
        routingPolicyRepository.putFactory(name, factory);
        return this;
    }

    /**
     * Adds a new routable factory to this protocol. This method is thread-safe, and may be invoked on a protocol object
     * that is already in use by a message bus instance. Notice that you must explicitly register a factory for each
     * supported version. You can always bypass this by passing a default version specification object to this function,
     * because that object will match any version.
     *
     * @param type    The routable type to assign a factory to.
     * @param factory The factory to add.
     * @param version The version for which this factory can be used.
     * @return This, to allow chaining.
     */
    public DocumentProtocol putRoutableFactory(int type, RoutableFactory factory, VersionSpecification version) {
        routableRepository.putFactory(version, type, factory);
        return this;
    }

    /**
     * Convenience method to call {@link #putRoutableFactory(int, RoutableFactory, com.yahoo.component.VersionSpecification)}
     * for multiple version specifications.
     *
     * @param type     The routable type to assign a factory to.
     * @param factory  The factory to add.
     * @param versions The versions for which this factory can be used.
     * @return This, to allow chaining.
     */
    public DocumentProtocol putRoutableFactory(int type, RoutableFactory factory, List<VersionSpecification> versions) {
        for (VersionSpecification version : versions) {
            putRoutableFactory(type, factory, version);
        }
        return this;
    }

    /**
     * Returns a string representation of the given error code.
     *
     * @param code The code whose string symbol to return.
     * @return The error string.
     */
    public static String getErrorName(int code) {
        switch (code) {
        case ERROR_MESSAGE_IGNORED:
            return "MESSAGE_IGNORED";
        case ERROR_POLICY_FAILURE:
            return "POLICY_FAILURE";
        case ERROR_DOCUMENT_NOT_FOUND:
            return "DOCUMENT_NOT_FOUND";
        case ERROR_DOCUMENT_EXISTS:
            return "DOCUMENT_EXISTS";
        case ERROR_BUCKET_NOT_FOUND:
            return "BUCKET_NOT_FOUND";
        case ERROR_BUCKET_DELETED:
            return "BUCKET_DELETED";
        case ERROR_NOT_IMPLEMENTED:
            return "NOT_IMPLEMENTED";
        case ERROR_ILLEGAL_PARAMETERS:
            return "ILLEGAL_PARAMETERS";
        case ERROR_IGNORED:
            return "IGNORED";
        case ERROR_UNKNOWN_COMMAND:
            return "UNKNOWN_COMMAND";
        case ERROR_UNPARSEABLE:
            return "UNPARSEABLE";
        case ERROR_NO_SPACE:
            return "NO_SPACE";
        case ERROR_INTERNAL_FAILURE:
            return "INTERNAL_FAILURE";
        case ERROR_PROCESSING_FAILURE:
            return "PROCESSING_FAILURE";
        case ERROR_TIMESTAMP_EXIST:
            return "TIMESTAMP_EXIST";
        case ERROR_STALE_TIMESTAMP:
            return "STALE_TIMESTAMP";
        case ERROR_NODE_NOT_READY:
            return "NODE_NOT_READY";
        case ERROR_WRONG_DISTRIBUTION:
            return "WRONG_DISTRIBUTION";
        case ERROR_REJECTED:
            return "REJECTED";
        case ERROR_ABORTED:
            return "ABORTED";
        case ERROR_BUSY:
            return "BUSY";
        case ERROR_NOT_CONNECTED:
            return "NOT_CONNECTED";
        case ERROR_DISK_FAILURE:
            return "DISK_FAILURE";
        case ERROR_IO_FAILURE:
            return "IO_FAILURE";
        case ERROR_SUSPENDED:
            return "SUSPENDED";
        case ERROR_TEST_AND_SET_CONDITION_FAILED:
            return "TEST_AND_SET_CONDITION_FAILED";
        default:
            return ErrorCode.getName(code);
        }
    }

    /**
     * This is a convenient entry to the {@link #merge(RoutingContext,Set)} method by way of a routing context object.
     * The replies of all child contexts are merged and stored in the context.
     *
     * @param ctx The context whose children to merge.
     */
    public static void merge(RoutingContext ctx) {
        merge(ctx, new HashSet<Integer>(0));
    }

    /**
     * This method implements the common way to merge document replies for whatever routing policy. In case of an error
     * in any of the replies, it will prepare an EmptyReply() and add all errors to it. If there are no errors, this
     * method will use the first reply in the list and transfer whatever feed answers might exist in the replies to it.
     *
     * @param ctx  The context whose children to merge.
     * @param mask The indexes of the children to skip.
     */
    public static void merge(RoutingContext ctx, Set<Integer> mask) {
        List<Reply> replies = new LinkedList<>();
        for (RoutingNodeIterator it = ctx.getChildIterator();
             it.isValid(); it.next()) {
            Reply ref = it.getReplyRef();
            replies.add(ref);
        }
        Tuple2<Integer, Reply> tuple = merge(replies, mask);
        if (tuple.first != null) {
            ctx.getChildIterator().skip(tuple.first).removeReply();
        }
        ctx.setReply(tuple.second);
    }

    private static Tuple2<Integer, Reply> merge(List<Reply> replies, Set<Integer> mask) {
        ReplyMerger rm = new ReplyMerger();
        for (int i = 0; i < replies.size(); ++i) {
            if (mask.contains(i)) {
                continue;
            }
            rm.merge(i, replies.get(i));
        }
        return rm.mergedReply();
    }

    /**
     * This method implements the common way to merge document replies for whatever routing policy. In case of an error
     * in any of the replies, it will prepare an EmptyReply() and add all errors to it. If there are no errors, this
     * method will use the first reply in the list and transfer whatever feed answers might exist in the replies to it.
     *
     *
     * @param replies The replies to merge.
     * @return The merged Reply.
     */
    public static Reply merge(List<Reply> replies) {
        return merge(replies, new HashSet<Integer>(0)).second;
    }

    /**
     * Returns true if the given reply has at least one error, and all errors are of the given type.
     *
     * @param reply   The reply to check for error.
     * @param errCode The error code to check for.
     * @return Whether or not the reply has only the given error code.
     */
    public static boolean hasOnlyErrorsOfType(Reply reply, int errCode) {
        if (!reply.hasErrors()) {
            return false;
        }
        for (int i = 0; i < reply.getNumErrors(); ++i) {
            if (reply.getError(i).getCode() != errCode) {
                return false;
            }
        }
        return true;
    }

    public String getName() {
        return NAME.toString();
    }

    public RoutingPolicy createPolicy(String name, String param) {
        return routingPolicyRepository.createPolicy(name, param);
    }

    public byte[] encode(Version version, Routable routable) {
        return routableRepository.encode(version, routable);
    }

    public Routable decode(Version version, byte[] data) {
        try {
            return routableRepository.decode(docMan, version, data);
        } catch (RuntimeException e) {
            e.printStackTrace();
            log.warning(e.getMessage());
            return null;
        }
    }

    /**
     * Returns a list of routable types that support the given version.
     *
     * @param version The version to return types for.
     * @return The list of supported types.
     */
    public List<Integer> getRoutableTypes(Version version) {
        return routableRepository.getRoutableTypes(version);
    }

    public MetricSet getMetrics() {
        return metrics;
    }

    final public DocumentTypeManager getDocumentTypeManager() { return docMan; }
}
