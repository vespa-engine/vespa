// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::Visitor
 * @ingroup storageserver
 *
 * @brief Base class for all visitors.
 *
 * A visitor is a piece of code existing in a shared library linked in, that
 * iterates serialized documents from the persistence layer
 */

#pragma once

#include "visitormessagesession.h"
#include "memory_bounded_trace.h"
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/storage/common/storagecomponent.h>
#include <vespa/storage/common/visitorfactory.h>
#include <vespa/documentapi/messagebus/messages/documentmessage.h>
#include <vespa/persistence/spi/selection.h>
#include <vespa/persistence/spi/read_consistency.h>
#include <list>
#include <deque>
#include <ostream>

namespace document {
    class Document;
    class DocumentId;
    namespace select { class Node; }
}
namespace vdslib { class Parameters; }

namespace documentapi {
    class DocumentMessage;
    class VisitorInfoMessage;
}

namespace storage {

namespace spi {
    class DocEntry;
}

namespace api {
    class ReturnCode;
    class StorageCommand;
    class StorageReply;
}

namespace framework { class MemoryAllocationType; }

class GetIterReply;
class CreateIteratorReply;
class Visitor;
struct VisitorThreadMetrics;

/**
 * To prevent circular dependency between visitors and visitor manager, this
 * interface is used to give visitor access to the functionality needed from
 * the manager.
 */
class VisitorMessageHandler {
public:
    virtual void send(const std::shared_ptr<api::StorageCommand>&, Visitor& visitor) = 0;
    virtual void send(const std::shared_ptr<api::StorageReply>&) = 0;
    /**
     * Called once when visitor shuts down and won't call this handler again.
     * The visitor may still have pending requests sent but not received though.
     */
    virtual void closed(api::VisitorId id) = 0;

    virtual ~VisitorMessageHandler() = default;
};

/**
 * Base class for Visitor implementations.
 *
 * Each visitor will implement this base class to become a visitor.
 * This base class takes care of talking to the persistence layer and
 * processing all the documents, calling the virtual functions each visitor
 * must implement. It also provides functions for sending data back to the
 * client.
 */
class Visitor
{
public:

    class HitCounter {
    public:
        HitCounter();
        void addHit(const document::DocumentId& hit, uint32_t size);
        void updateVisitorStatistics(vdslib::VisitorStatistics& statistics) const;
    private:
        uint32_t _doc_hits;
        uint64_t _doc_bytes;
    };

    enum VisitorState
    {
        STATE_NOT_STARTED,
        STATE_RUNNING,
        STATE_CLOSING,
        STATE_COMPLETED
    };

    static constexpr size_t TRANSIENT_ERROR_RETRIES_BEFORE_NOTIFY = 7;

private:
    friend class BucketIterationState;
    /** Holds status information on progress visiting a single bucket.
     * Also serves as a guard for ensuring we send down a DestroyVisitor
     * command when a state instance is destroyed and its iterator id is
     * non-zero.
     */
    class BucketIterationState : public document::Printable
    {
    private:
        Visitor& _visitor;
        VisitorMessageHandler& _messageHandler;
    public:
        document::Bucket _bucket;
        spi::IteratorId _iteratorId;
        uint32_t _pendingIterators;
        bool _completed;

        BucketIterationState(Visitor& visitor,
                             VisitorMessageHandler& messageHandler,
                             const document::Bucket &bucket)
            : _visitor(visitor),
              _messageHandler(messageHandler),
              _bucket(bucket),
              _iteratorId(0),
              _pendingIterators(0),
              _completed(false)
        {}

        /** Sends DestroyIterator over _messageHandler if _iteratorId != 0 */
        ~BucketIterationState();

        void setCompleted(bool completed = true) { _completed = completed; }
        bool isCompleted() const { return _completed; }

        document::Bucket   getBucket() const { return _bucket; }
        document::BucketId getBucketId() const { return _bucket.getBucketId(); }

        void setIteratorId(spi::IteratorId iteratorId) {
            _iteratorId = iteratorId;
        }
        spi::IteratorId getIteratorId() const { return _iteratorId; }

        void setPendingControlCommand() {
            _iteratorId = spi::IteratorId(0);
        }

        bool hasPendingControlCommand() const {
            return _iteratorId == spi::IteratorId(0);
        }

        bool hasPendingIterators() const { return _pendingIterators > 0; }

        void print(std::ostream& out, bool, const std::string& ) const override {
            out << "BucketIterationState("
                << _bucket.getBucketId()
                << ", pending GetIters: " << _pendingIterators
                << ", iterator id: " << _iteratorId
                << ", completed: " << (_completed ? "yes" : "no")
                << ")";
        }
    };

    struct VisitorOptions
    {
        // Minimum timestamp to visit.
        framework::MicroSecTime _fromTime;
        // Maximum timestamp to visit.
        framework::MicroSecTime _toTime;

        // Maximum number of buckets that can be visited in parallel
        uint32_t _maxParallel;
        // Number of pending get iter operations per bucket
        uint32_t _maxParallelOneBucket;

        // Maximum number of messages sent to clients that have not yet been
        // replied to (max size to _sentMessages map)
        uint32_t _maxPending;

        std::string _fieldSet;
        bool _visitRemoves;

        VisitorOptions();
    };

    struct VisitorTarget
    {
        uint64_t _pendingMessageId;

        struct MessageMeta {
            MessageMeta(uint64_t msgId,
                        std::unique_ptr<documentapi::DocumentMessage> msg);
            MessageMeta(MessageMeta&&) noexcept;
            ~MessageMeta();

            MessageMeta& operator=(MessageMeta&&) noexcept;

            MessageMeta(const MessageMeta&) = delete;
            MessageMeta& operator=(const MessageMeta&) = delete;

            uint64_t messageId;
            uint32_t retryCount;
            // Memory usage for message the meta object was created with.
            uint32_t memoryUsage;
            std::unique_ptr<documentapi::DocumentMessage> message;
            std::string messageText;
        };

        /**
         * Keeps track of all the metadata for both pending and queued messages.
         */
        std::map<uint64_t, MessageMeta> _messageMeta;

        /**
         * Invariants:
         *   _memoryUsage == sum of m.memoryUsage for all m in _messageMeta
         */
        uint32_t _memoryUsage;

        /**
         * Contains the list of messages currently being sent to the client.
         * Value refers to the message id (key in _messageMeta).
         */
        std::set<uint64_t> _pendingMessages;

        // Maps from time sent to message to send.
        // Value refers to message id (key in _messageMeta).
        typedef std::multimap<framework::MicroSecTime, uint64_t> MessageQueue;

        MessageQueue _queuedMessages;

        MessageMeta& insertMessage(
                std::unique_ptr<documentapi::DocumentMessage>);
        /**
         * Preconditions:
         *   msgId exists as a key in _messageMeta
         */
        MessageMeta& metaForMessageId(uint64_t msgId);
        MessageMeta releaseMetaForMessageId(uint64_t msgId);
        void reinsertMeta(MessageMeta);

        bool hasQueuedMessages() const { return !_queuedMessages.empty(); }
        void discardQueuedMessages();

        uint32_t getMemoryUsage() const noexcept {
            return _memoryUsage;
        }

        VisitorTarget();
        ~VisitorTarget();
    };

protected:
    StorageComponent& _component;

private:
    VisitorOptions _visitorOptions;
    VisitorTarget _visitorTarget;
    VisitorState _state;

    // The list of buckets to visit.
    std::vector<document::BucketId> _buckets;
    document::BucketSpace _bucketSpace;

    // The iterator iterating the buckets to visit.
    uint32_t _currentBucket;
    // The states of the buckets currently being visited.
    typedef std::list<BucketIterationState*> BucketStateList;
    BucketStateList _bucketStates;
    // Set to true after performing given callbacks
    bool _calledStartingVisitor;
    bool _calledCompletedVisitor;

    framework::MicroSecTime _startTime;

    bool _hasSentReply;

    uint32_t _docBlockSize;
    uint32_t _memoryUsageLimit;
    framework::MilliSecTime _docBlockTimeout;
    framework::MilliSecTime _visitorInfoTimeout;
    uint32_t _serialNumber;
    // Keep trace level independent of _initiatingCmd, since we might want to
    // print out the trace level even after the command's ownership has been
    // released away from us.
    uint32_t _traceLevel;
    uint16_t _ownNodeIndex;

    // Used by visitor client to identify what visitor messages belong to
    api::StorageMessage::Id _visitorCmdId;
    api::VisitorId _visitorId;
    std::shared_ptr<api::CreateVisitorCommand> _initiatingCmd;
    api::StorageMessage::Priority _priority;

    api::ReturnCode _result;
    std::map<std::string, framework::MicroSecTime> _recentlySentErrorMessages;
    framework::MicroSecTime _timeToDie; // Visitor will time out to distributor at this time

    std::unique_ptr<HitCounter> _hitCounter;

    static constexpr size_t DEFAULT_TRACE_MEMORY_LIMIT = 65536;
    MemoryBoundedTrace _trace;

    Visitor(const Visitor &);
    Visitor& operator=(const Visitor &);

protected:
    // These variables should not be altered after visitor starts. This not
    // controlled by locks.
    VisitorMessageHandler* _messageHandler;
    VisitorMessageSession::UP _messageSession;
    documentapi::Priority::Value _documentPriority;

    std::string _id;
    std::unique_ptr<mbus::Route> _controlDestination;
    std::unique_ptr<mbus::Route> _dataDestination;
    std::shared_ptr<document::select::Node> _documentSelection;
    std::string _documentSelectionString;
    vdslib::VisitorStatistics _visitorStatistics;

    bool isCompletedCalled() const { return _calledCompletedVisitor; }

    uint32_t traceLevel() const noexcept { return _traceLevel; }

    /**
     * Attempts to add the given trace message to the internal, memory bounded
     * trace tree. Message will not be added if the trace is already exceeding
     * maximum memory limits.
     *
     * Returns true iff message was added to internal trace tree.
     */
    bool addBoundedTrace(uint32_t level, const vespalib::string& message);

    const vdslib::Parameters& visitor_parameters() const noexcept;

    // Possibly modifies the ReturnCode parameter in-place if its return code should
    // be changed based on visitor subclass-specific behavior.
    // Returns true if the visitor itself should be failed (aborted) with the
    // error code, false if the DocumentAPI message should be retried later.
    [[nodiscard]] virtual bool remap_docapi_message_error_code(api::ReturnCode& in_out_code);
public:
    using DocEntryList = std::vector<std::unique_ptr<spi::DocEntry>>;
    Visitor(StorageComponent& component);
    virtual ~Visitor();

    framework::MicroSecTime getStartTime() const { return _startTime; }
    api::VisitorId getVisitorId() const { return _visitorId; }
    const std::string& getVisitorName() const { return _id; }
    const mbus::Route* getControlDestination() const {
        return _controlDestination.get(); // Can't be null if attached
    }
    const mbus::Route* getDataDestination() const {
        return _dataDestination.get(); // Can't be null if attached
    }

    void setMaxPending(unsigned int maxPending)
        { _visitorOptions._maxPending = maxPending; }

    void setFieldSet(const std::string& fieldSet) { _visitorOptions._fieldSet = fieldSet; }
    void visitRemoves() { _visitorOptions._visitRemoves = true; }
    void setDocBlockSize(uint32_t size) { _docBlockSize = size; }
    uint32_t getDocBlockSize() const { return _docBlockSize; }
    void setMemoryUsageLimit(uint32_t limit) noexcept {
        _memoryUsageLimit = limit;
    }
    uint32_t getMemoryUsageLimit() const noexcept {
        return _memoryUsageLimit;
    }
    void setDocBlockTimeout(framework::MilliSecTime timeout)
        { _docBlockTimeout = timeout; }
    void setVisitorInfoTimeout(framework::MilliSecTime timeout)
        { _visitorInfoTimeout = timeout; }
    void setOwnNodeIndex(uint16_t nodeIndex) { _ownNodeIndex = nodeIndex; }
    void setBucketSpace(document::BucketSpace bucketSpace) { _bucketSpace = bucketSpace; }

    /** Override this to know which buckets are currently being visited. */
    virtual void startingVisitor(const std::vector<document::BucketId>&) {}

    /**
     * Override this method to receive a callback whenever a new
     * vector of documents arrive from the persistence layer.
     */
    virtual void handleDocuments(const document::BucketId&,
                                 DocEntryList & entries,
                                 HitCounter& hitCounter) = 0;

    /**
     * Override this if you want to do anything special after bucket completes.
     */
    virtual void completedBucket(const document::BucketId&, HitCounter&) {}

    /**
     * Override this if you want to know if visiting is aborted. Note that you
     * cannot use this callback to send anything.
     */
    virtual void abortedVisiting() {}

    /**
     * Override if you want to know when the whole visitor has completed.
     */
    virtual void completedVisiting(HitCounter&) {}

    /**
     * By default a visitor requires strong consistency on its reads, i.e.
     * previously ACKed writes MUST be visible to the operation. Visitor
     * subclasses might choose to override this if their requirements are more
     * lax than the deafult of STRONG.
     * 
     * The consistency level provided here is propagated through the SPI
     * Context object for createIterator calls.
     */
    virtual spi::ReadConsistency getRequiredReadConsistency() const {
        return spi::ReadConsistency::STRONG;
    }

    /** Subclass should call this to indicate error conditions. */
    void fail(const api::ReturnCode& reason,
              bool overrideExistingError = false);

    /**
     * Used to silence transient errors that can happen during normal operation.
     */
    bool shouldReportProblemToClient(const api::ReturnCode&,
                                     size_t retryCount) const;

    /** Called to send report to client of potential non-critical problems. */
    void reportProblem(const std::string& problem);

    /**
     * Wrapper for reportProblem which reports string representation of
     * result code and message
     **/
    void reportProblem(const api::ReturnCode& problemCode);

    /** Call to gracefully close visitor */
    void close();

    /**
     * Called before deleting this visitor.
     * Precondition: visitor state must be STATE_COMPLETED.
     **/
    void finalize();

    /** Call -ONLY- during process shutdown case where you don't care if
     * we end up leaking persistence provider layer iterators. Cannot
     * gracefully close in this case since we shut down the event handler
     * thread in advance.
     */
    void forceClose();

    void start(api::VisitorId id, api::StorageMessage::Id cmdId,
               const std::string& name,
               const std::vector<document::BucketId>&,
               framework::MicroSecTime fromTimestamp,
               framework::MicroSecTime toTimestamp,
               std::unique_ptr<document::select::Node> docSelection,
               const std::string& docSelectionString,
               VisitorMessageHandler&,
               VisitorMessageSession::UP,
               documentapi::Priority::Value);

    void attach(std::shared_ptr<api::CreateVisitorCommand> initiatingCmd,
                const mbus::Route& controlAddress,
                const mbus::Route& dataAddress,
                framework::MilliSecTime timeout);

    void handleDocumentApiReply(mbus::Reply::UP reply,
                                VisitorThreadMetrics& metrics);

    void onGetIterReply(const std::shared_ptr<GetIterReply>& reply,
                        VisitorThreadMetrics& metrics);

    void onCreateIteratorReply(
            const std::shared_ptr<CreateIteratorReply>& reply,
            VisitorThreadMetrics& metrics);

    bool failed() const { return _result.failed(); }

    /**
     * This function will check current state and make the visitor move on, if
     * there are any space left in queues.
     */
    bool continueVisitor();

    void getStatus(std::ostream& out, bool verbose) const;

    void setMaxParallel(uint32_t maxParallel)
        { _visitorOptions._maxParallel = maxParallel; }
    void setMaxParallelPerBucket(uint32_t max)
        { _visitorOptions._maxParallelOneBucket = max; }

    /**
     * Sends a message to the data handler for this visitor.
     */
    void sendMessage(std::unique_ptr<documentapi::DocumentMessage> documentMessage);

    bool isRunning() const { return _state == STATE_RUNNING; }
    bool isCompleted() const { return _state == STATE_COMPLETED; }

private:
    /**
     * Sends a message to the control handler for this visitor.
     * Utility function used by fail() and reportProblem() for instance.
     */
    void sendInfoMessage(std::unique_ptr<documentapi::VisitorInfoMessage> cmd);

    /**
     * This function will inspect the bucket states and possibly request
     * new iterators. It is called fairly often (everytime there are free spots
     * on message queue), thus it is unnecessary to process all buckets at once.
     * Buckets are thus processed in a round robin fashion.
     *
     * @return False if there is no more to iterate.
     */
    bool getIterators();

    /**
     * Attempt to send the message kept in msgMeta over the destination session,
     * automatically queuing for future transmission if a maximum number of
     * messages are already pending.
     *
     * Preconditions:
     *   msgMeta must be in _visitorTarget._messageMeta
     *   msgMeta.message.get() != nullptr
     * Postconditions:
     *   case enqueued:
     *     msgMeta.messageId in _visitorTarget._queuedMessages
     *   case sent:
     *     msgMeta.message.get() == nullptr (released to message bus)
     *   case send failure:
     *     visitor transition to STATE_FAILURE
     */
    void sendDocumentApiMessage(VisitorTarget::MessageMeta& msgMeta);

    void sendReplyOnce();

    bool hasFailedVisiting() const { return _result.failed(); }

    bool hasPendingIterators() const { return !_bucketStates.empty(); }

    bool mayTransitionToCompleted() const;

    void discardAllNoPendingBucketStates();

    static const char* getStateName(VisitorState);

    /**
     * (Re-)send any queued messages whose time-to-send has been reached.
     * Ensures number of resulting pending messages from visitor does not
     * violate maximum pending options.
     */
    void sendDueQueuedMessages(framework::MicroSecTime timeNow);

    /**
     * Whether visitor should enable and forward message bus traces for messages
     * going via DocumentAPI or through the SPI.
     *
     * Precondition: attach() must have been called on `this`.
     */
    bool shouldAddMbusTrace() const noexcept {
        return _traceLevel != 0;
    }

    /**
     * Set internal state to the given state value.
     * @return Old state.
     */
    VisitorState transitionTo(VisitorState newState);
};

// Visitors use custom tracing logic to control the amount of memory used by
// trace nodes. Wrap this in a somewhat more convenient macro to hide the details.
// Can only be called by Visitor or its subclasses.
#define VISITOR_TRACE(level, message) \
    do { \
        if (traceLevel() >= (level)) { \
            addBoundedTrace(level, message); \
        } \
    } while (false);


} // storage
