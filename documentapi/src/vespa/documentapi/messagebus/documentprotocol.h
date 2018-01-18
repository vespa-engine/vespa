// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/documentapi/messagebus/systemstate/systemstate.h>
#include <vespa/messagebus/errorcode.h>
#include <vespa/messagebus/iprotocol.h>
#include <vespa/messagebus/reply.h>
#include <vespa/messagebus/routing/routingcontext.h>

namespace vespalib {
    class VersionSpecification;
}
namespace document {
    class DocumentTypeRepo;
    class ByteBuffer;
}

namespace documentapi {

class LoadTypeSet;
class RoutingPolicyRepository;
class RoutableRepository;
class SystemState;
class IRoutingPolicyFactory;
class IRoutableFactory;

class DocumentProtocol final : public mbus::IProtocol {
private:
    std::unique_ptr<RoutingPolicyRepository>    _routingPolicyRepository;
    std::unique_ptr<RoutableRepository>         _routableRepository;
    std::unique_ptr<SystemState>                _systemState;
    std::shared_ptr<document::DocumentTypeRepo> _repo;

public:
    /**
     * Convenience typedef.
     */
    typedef std::unique_ptr<DocumentProtocol> UP;
    typedef std::shared_ptr<DocumentProtocol> SP;

    /**
     * The name of this protocol is public static so it can be referenced by all of this protocol's messages
     * and replies instead of hard coding the string in every class.
     */
    static const mbus::string NAME;

    /**
     * Defines all message and reply types that are implemented by this protocol.
     */
    enum MessageType {
        DOCUMENT_MESSAGE            = 100000,
//        MESSAGE_STARTOFFEED         = DOCUMENT_MESSAGE + 1,
//        MESSAGE_ENDOFFEED           = DOCUMENT_MESSAGE + 2,
        MESSAGE_GETDOCUMENT         = DOCUMENT_MESSAGE + 3,
        MESSAGE_PUTDOCUMENT         = DOCUMENT_MESSAGE + 4,
        MESSAGE_REMOVEDOCUMENT      = DOCUMENT_MESSAGE + 5,
        MESSAGE_UPDATEDOCUMENT      = DOCUMENT_MESSAGE + 6,
        MESSAGE_CREATEVISITOR       = DOCUMENT_MESSAGE + 7,
        MESSAGE_DESTROYVISITOR      = DOCUMENT_MESSAGE + 8,
        MESSAGE_VISITORINFO         = DOCUMENT_MESSAGE + 9,
        MESSAGE_SEARCHRESULT        = DOCUMENT_MESSAGE + 11,
        MESSAGE_MULTIOPERATION      = DOCUMENT_MESSAGE + 13,
        MESSAGE_DOCUMENTSUMMARY     = DOCUMENT_MESSAGE + 14,
        MESSAGE_MAPVISITOR          = DOCUMENT_MESSAGE + 15,
        MESSAGE_GETBUCKETSTATE      = DOCUMENT_MESSAGE + 18,
        MESSAGE_STATBUCKET          = DOCUMENT_MESSAGE + 19,
        MESSAGE_GETBUCKETLIST       = DOCUMENT_MESSAGE + 20,
        MESSAGE_DOCUMENTLIST        = DOCUMENT_MESSAGE + 21,
        MESSAGE_EMPTYBUCKETS        = DOCUMENT_MESSAGE + 23,
        MESSAGE_REMOVELOCATION      = DOCUMENT_MESSAGE + 24,
        MESSAGE_QUERYRESULT         = DOCUMENT_MESSAGE + 25,
        MESSAGE_BATCHDOCUMENTUPDATE = DOCUMENT_MESSAGE + 26,
//        MESSAGE_GARBAGECOLLECT      = DOCUMENT_MESSAGE + 27,

        DOCUMENT_REPLY              = 200000,
//        REPLY_STARTOFFEED           = DOCUMENT_REPLY + 1,
//        REPLY_ENDOFFEED             = DOCUMENT_REPLY + 2,
        REPLY_GETDOCUMENT           = DOCUMENT_REPLY + 3,
        REPLY_PUTDOCUMENT           = DOCUMENT_REPLY + 4,
        REPLY_REMOVEDOCUMENT        = DOCUMENT_REPLY + 5,
        REPLY_UPDATEDOCUMENT        = DOCUMENT_REPLY + 6,
        REPLY_CREATEVISITOR         = DOCUMENT_REPLY + 7,
        REPLY_DESTROYVISITOR        = DOCUMENT_REPLY + 8,
        REPLY_VISITORINFO           = DOCUMENT_REPLY + 9,
        REPLY_SEARCHRESULT          = DOCUMENT_REPLY + 11,
        REPLY_MULTIOPERATION        = DOCUMENT_REPLY + 13,
        REPLY_DOCUMENTSUMMARY       = DOCUMENT_REPLY + 14,
        REPLY_MAPVISITOR            = DOCUMENT_REPLY + 15,
        REPLY_GETBUCKETSTATE        = DOCUMENT_REPLY + 18,
        REPLY_STATBUCKET            = DOCUMENT_REPLY + 19,
        REPLY_GETBUCKETLIST         = DOCUMENT_REPLY + 20,
        REPLY_DOCUMENTLIST          = DOCUMENT_REPLY + 21,
        REPLY_EMPTYBUCKETS          = DOCUMENT_REPLY + 23,
        REPLY_REMOVELOCATION        = DOCUMENT_REPLY + 24,
        REPLY_QUERYRESULT           = DOCUMENT_REPLY + 25,
        REPLY_BATCHDOCUMENTUPDATE   = DOCUMENT_REPLY + 26,
//        REPLY_GARBAGECOLLECT        = DOCUMENT_REPLY + 27,
        REPLY_WRONGDISTRIBUTION     = DOCUMENT_REPLY + 1000,
        REPLY_DOCUMENTIGNORED       = DOCUMENT_REPLY + 1001
    };

    /**
     * Defines all extended errors that are used by this protocol.
     */
    enum {
        /** Used by policies to indicate an inappropriate message. */
        ERROR_MESSAGE_IGNORED               = mbus::ErrorCode::APP_FATAL_ERROR + 1,

        /** Used for error policy when policy creation failed. */
        ERROR_POLICY_FAILURE                = mbus::ErrorCode::APP_FATAL_ERROR + 2,

        // Error codes to represent various failures that can come from VDS. All
        // indexed from fatal error or transient failure plus 1000-1999

        /** Document in operation cannot be found. (VDS Get and Remove) */
        ERROR_DOCUMENT_NOT_FOUND            = mbus::ErrorCode::APP_FATAL_ERROR + 1001,
        /**
         * Operation cannot be performed because token already exist.
         * (Create bucket, create visitor)
         */
        ERROR_EXISTS                        = mbus::ErrorCode::APP_FATAL_ERROR + 1002,

        ERROR_NOT_IMPLEMENTED               = mbus::ErrorCode::APP_FATAL_ERROR + 1004,
        /** Parameters given in request is illegal. */
        ERROR_ILLEGAL_PARAMETERS            = mbus::ErrorCode::APP_FATAL_ERROR + 1005,
        /** Unknown request received. (New client requesting from old server) */
        ERROR_UNKNOWN_COMMAND               = mbus::ErrorCode::APP_FATAL_ERROR + 1007,
        /** Request cannot be decoded. */
        ERROR_UNPARSEABLE                   = mbus::ErrorCode::APP_FATAL_ERROR + 1008,
        /** Not enough free space on disk to perform operation. */
        ERROR_NO_SPACE                      = mbus::ErrorCode::APP_FATAL_ERROR + 1009,
        /** Request was not handled correctly. */
        ERROR_IGNORED                       = mbus::ErrorCode::APP_FATAL_ERROR + 1010,
        /** We failed in some way we didn't expect to fail. */
        ERROR_INTERNAL_FAILURE              = mbus::ErrorCode::APP_FATAL_ERROR + 1011,
        /** Node refuse to perform operation. (Illegally formed message?) */
        ERROR_REJECTED                      = mbus::ErrorCode::APP_FATAL_ERROR + 1012,
        /** Test and set condition (selection) failed. */
        ERROR_TEST_AND_SET_CONDITION_FAILED = mbus::ErrorCode::APP_FATAL_ERROR + 1013,

        /** Node not ready to perform operation. (Initializing VDS nodes) */
        ERROR_NODE_NOT_READY                = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1001,
        /**
         * Wrong node to talk to in current state.
         * (VDS system state disagreement)
         */
        ERROR_WRONG_DISTRIBUTION = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1002,
        /** Operation cut short and aborted. (Destroy visitor, node stopping) */
        ERROR_ABORTED                       = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1004,
        /** Node too busy to process request (Typically full queues) */
        ERROR_BUSY                          = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1005,
        /** Lost connection with the node we requested something from. */
        ERROR_NOT_CONNECTED                 = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1006,

        /** Node have not implemented support for the given operation. */
        /**
         * We failed accessing the disk, which we think is a disk hardware
         * problem.
         */
        ERROR_DISK_FAILURE                  = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1007,
        /**
         * We failed during an IO operation, we dont think is a specific disk
         * hardware problem.
         */
        ERROR_IO_FAILURE                    = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1008,
        /**
         * Bucket given in operation not found due to bucket database
         * inconsistencies between storage and distributor nodes.
         */
        ERROR_BUCKET_NOT_FOUND              = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1009,
        /**
         * Bucket recently removed, such that operation cannot be performed.
         * Differs from BUCKET_NOT_FOUND in that there is no db inconsistency.
         */
        ERROR_BUCKET_DELETED                = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1012,
	/**
	 * Storage node received a timestamp that is stale. Likely clock skew.
	 */
        ERROR_STALE_TIMESTAMP               = mbus::ErrorCode::APP_TRANSIENT_ERROR + 1013,

        // Error codes for docproc

        /** Failed to process the given request. (Used by docproc) */
        ERROR_PROCESSING_FAILURE            = mbus::ErrorCode::APP_FATAL_ERROR + 2001,
        /** Unique timestamp specified for new operation is already in use. */
        ERROR_TIMESTAMP_EXIST               = mbus::ErrorCode::APP_FATAL_ERROR + 2002,

        /**
         * The given node have gotten a critical error and have suspended
         * itself.  (Docproc nodes do this then they can't function anymore)
         */
        ERROR_SUSPENDED                     = mbus::ErrorCode::APP_TRANSIENT_ERROR + 2001
    };

public:
    /**
     * Constructs a new document protocol using the given id for config subscription.
     *
     * @param configId The id to use when subscribing to config.
     */
    DocumentProtocol(const LoadTypeSet& loadTypes,
                     std::shared_ptr<document::DocumentTypeRepo> repo,
                     const string &configId = "");
    ~DocumentProtocol();

    /**
     * Adds a new routable factory to this protocol. This method is thread-safe, and may be invoked on a
     * protocol object that is already in use by a message bus instance. Notice that the name you supply for a
     * factory is the case-sensitive name that will be referenced by routes.
     *
     * @param name    The name of the factory to add.
     * @param factory The factory to add.
     * @return This, to allow chaining.
     */
    DocumentProtocol &putRoutingPolicyFactory(const string &name, std::shared_ptr<IRoutingPolicyFactory> factory);

    /**
     * Adds a new routable factory to this protocol. This method is thread-safe, and may be invoked on a
     * protocol object that is already in use by a message bus instance. Notice that you must explicitly
     * register a factory for each supported version. You can always bypass this by passing a default version
     * specification object to this function, because that object will match any version.
     *
     * @param type    The routable type to assign a factory to.
     * @param factory The factory to add.
     * @param version The version for which this factory can be used.
     * @return This, to allow chaining.
     */
    DocumentProtocol &putRoutableFactory(uint32_t type, std::shared_ptr<IRoutableFactory> factory,
                                         const vespalib::VersionSpecification &version);

    /**
     * Convenience method to call {@link #putRoutableFactory(int, RoutableFactory,
     * com.yahoo.component.VersionSpecification)} for multiple version specifications.
     *
     * @param type     The routable type to assign a factory to.
     * @param factory  The factory to add.
     * @param versions The versions for which this factory can be used.
     * @return This, to allow chaining.
     */
    DocumentProtocol &putRoutableFactory(uint32_t type, std::shared_ptr<IRoutableFactory> factory,
                                         const std::vector<vespalib::VersionSpecification> &versions);

    /**
     * Returns a list of routable types that support the given version.
     *
     * @param version The version to return types for.
     * @param out     The list to write to.
     * @return The number of supported types.
     */
    uint32_t getRoutableTypes(const vespalib::Version &version, std::vector<uint32_t> &out) const;

    /**
     * Returns a string representation of the given error code.
     *
     * @param errorCode The code whose string symbol to return.
     * @return The error string.
     */
    static string getErrorName(uint32_t errorCode);

    /**
     * Deserialized the given type of routable from the given byte buffer.
     *
     * @param type The type of routable.
     * @param buf A byte buffer that contains a serialized routable.
     * @return The deserialized routable.
     */
    mbus::Routable::UP deserialize(uint32_t type, document::ByteBuffer &buf) const;

    /**
     * This is a convenient entry to the {@link #merge(RoutingContext,std::set)} method by way of a routing
     * context object. The replies of all child contexts are merged and stored in the context.
     *
     * @param ctx The context whose children to merge.
     */
    static void merge(mbus::RoutingContext &ctx);

    /**
     * This method implements the common way to merge document replies for whatever routing policy. In case of
     * an error in any of the replies, it will prepare an EmptyReply() and add all errors to it. If there are
     * no errors, this method will use the first reply in the list and transfer whatever feed answers might
     * exist in the replies to it.
     *
     * @param ctx  The context whose children to merge.
     * @param mask The indexes of the children to skip.
     */
    static void merge(mbus::RoutingContext &ctx, const std::set<uint32_t> &mask);

    /**
     * Returns true if the given reply has at least one error, and all errors
     * are of the given type.
     *
     * @param reply The reply to check for error.
     * @param errCode  The error code to check for.
     * @return Whether or not the reply has only the given error code.
     */
    static bool hasOnlyErrorsOfType(const mbus::Reply &reply, uint32_t errCode);

    /**
     * Returns the curren state of the system, as observed by this protocol. This state object may be freely
     * modified by the caller.
     *
     * @return The system state.
     */
    SystemState &getSystemState() { return *_systemState; }
    const mbus::string &getName() const override { return NAME; }
    mbus::IRoutingPolicy::UP createPolicy(const mbus::string &name, const mbus::string &param) const override;
    mbus::Blob encode(const vespalib::Version &version, const mbus::Routable &routable) const override;
    mbus::Routable::UP decode(const vespalib::Version &version, mbus::BlobRef data) const override;
    bool requireSequencing() const override { return false; }
};

}
