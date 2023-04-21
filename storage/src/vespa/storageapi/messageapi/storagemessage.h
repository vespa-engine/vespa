// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * @class storage::api::StorageMessage
 * @ingroup messageapi
 *
 * @brief Superclass for all storage messages.
 *
 * @version $Id$
 */

#pragma once

#include "messagehandler.h"
#include <vespa/messagebus/routing/route.h>
#include <vespa/messagebus/trace.h>
#include <vespa/vdslib/state/nodetype.h>
#include <vespa/document/bucket/bucket.h>
#include <vespa/vespalib/util/printable.h>
#include <map>
#include <iosfwd>

namespace vespalib { class asciistream; }
// The following macros are provided as a way to write storage messages simply.
// They implement the parts of the code that can easily be automaticly
// generated.

/**
 * Adds a messagehandler callback and some utilities
 */
#define DECLARE_POINTER_TYPEDEFS(message) \
    using UP = std::unique_ptr<message>; \
    using SP = std::shared_ptr<message>; \
    using CSP = std::shared_ptr<const message>;

#define DECLARE_STORAGEREPLY(reply, callback) \
public: \
    DECLARE_POINTER_TYPEDEFS(reply) \
private: \
    bool callHandler(storage::api::MessageHandler& h, \
                     const std::shared_ptr<storage::api::StorageMessage>& m) const override \
    { \
        return h.callback(std::static_pointer_cast<reply>(m)); \
    }

/** Commands also has a command to implement to create the reply. */
#define DECLARE_STORAGECOMMAND(command, callback) \
public: \
    std::unique_ptr<storage::api::StorageReply> makeReply() override; \
    DECLARE_STORAGEREPLY(command, callback)

/** This macro implements common stuff for all storage messages. */
#define IMPLEMENT_COMMON(message) \

/** This macro is used to implement common storage reply functionality. */
#define IMPLEMENT_REPLY(reply) \
    IMPLEMENT_COMMON(reply) \

/** This macro is used to implement common storage command functionality. */
#define IMPLEMENT_COMMAND(command, reply) \
    IMPLEMENT_COMMON(command) \
    std::unique_ptr<storage::api::StorageReply> \
    storage::api::command::makeReply() \
    { \
        return std::make_unique<reply>(*this); \
    }

namespace storage::api {

using duration = vespalib::duration;

/**
 * @class MessageType
 * @ingroup messageapi
 *
 * @brief This class defines the different message types we have.
 *
 * This is used to be able to deserialize messages of various classes.
 */
class MessageType : public vespalib::Printable {
public:
    enum Id {
        GET_ID = 4,
        GET_REPLY_ID = 5,
        INTERNAL_ID = 6,
        INTERNAL_REPLY_ID = 7,
        PUT_ID = 10,
        PUT_REPLY_ID = 11,
        REMOVE_ID = 12,
        REMOVE_REPLY_ID = 13,
        REVERT_ID = 14,
        REVERT_REPLY_ID = 15,
        STAT_ID = 16,
        STAT_REPLY_ID = 17,
        VISITOR_CREATE_ID = 18,
        VISITOR_CREATE_REPLY_ID = 19,
        VISITOR_DESTROY_ID = 20,
        VISITOR_DESTROY_REPLY_ID = 21,
        CREATEBUCKET_ID = 26,
        CREATEBUCKET_REPLY_ID = 27,
        MERGEBUCKET_ID = 32,
        MERGEBUCKET_REPLY_ID = 33,
        DELETEBUCKET_ID = 34,
        DELETEBUCKET_REPLY_ID = 35,
        SETNODESTATE_ID = 36,
        SETNODESTATE_REPLY_ID = 37,
        GETNODESTATE_ID = 38,
        GETNODESTATE_REPLY_ID = 39,
        SETSYSTEMSTATE_ID = 40,
        SETSYSTEMSTATE_REPLY_ID = 41,
        GETSYSTEMSTATE_ID = 42,
        GETSYSTEMSTATE_REPLY_ID = 43,
        GETBUCKETDIFF_ID = 50,
        GETBUCKETDIFF_REPLY_ID = 51,
        APPLYBUCKETDIFF_ID = 52,
        APPLYBUCKETDIFF_REPLY_ID = 53,
        REQUESTBUCKETINFO_ID = 54,
        REQUESTBUCKETINFO_REPLY_ID = 55,
        NOTIFYBUCKETCHANGE_ID = 56,
        NOTIFYBUCKETCHANGE_REPLY_ID = 57,
        DOCBLOCK_ID = 58,
        DOCBLOCK_REPLY_ID = 59,
        VISITOR_INFO_ID = 60,
        VISITOR_INFO_REPLY_ID = 61,
        // SEARCHRESULT_ID = 64,
        // SEARCHRESULT_REPLY_ID = 65,
        SPLITBUCKET_ID = 66,
        SPLITBUCKET_REPLY_ID = 67,
        JOINBUCKETS_ID = 68,
        JOINBUCKETS_REPLY_ID = 69,
        // DOCUMENTSUMMARY_ID = 72,
        // DOCUMENTSUMMARY_REPLY_ID = 73,
        MAPVISITOR_ID = 74,
        MAPVISITOR_REPLY_ID = 75,
        STATBUCKET_ID = 76,
        STATBUCKET_REPLY_ID = 77,
        GETBUCKETLIST_ID = 78,
        GETBUCKETLIST_REPLY_ID = 79,
        DOCUMENTLIST_ID = 80,
        DOCUMENTLIST_REPLY_ID = 81,
        UPDATE_ID = 82,
        UPDATE_REPLY_ID = 83,
        EMPTYBUCKETS_ID = 84,
        EMPTYBUCKETS_REPLY_ID = 85,
        REMOVELOCATION_ID = 86,
        REMOVELOCATION_REPLY_ID = 87,
        QUERYRESULT_ID = 88,
        QUERYRESULT_REPLY_ID = 89,
        SETBUCKETSTATE_ID = 94,
        SETBUCKETSTATE_REPLY_ID = 95,
        ACTIVATE_CLUSTER_STATE_VERSION_ID = 96,
        ACTIVATE_CLUSTER_STATE_VERSION_REPLY_ID = 97,
        MESSAGETYPE_MAX_ID
    };

private:
    static std::map<Id, MessageType*> _codes;
    const vespalib::string _name;
    Id _id;
    MessageType *_reply;
    const MessageType *_replyOf;

    MessageType(vespalib::stringref name, Id id, const MessageType* replyOf = 0);
public:
    static const MessageType DOCBLOCK;
    static const MessageType DOCBLOCK_REPLY;
    static const MessageType GET;
    static const MessageType GET_REPLY;
    static const MessageType INTERNAL;
    static const MessageType INTERNAL_REPLY;
    static const MessageType PUT;
    static const MessageType PUT_REPLY;
    static const MessageType REMOVE;
    static const MessageType REMOVE_REPLY;
    static const MessageType REVERT;
    static const MessageType REVERT_REPLY;
    static const MessageType VISITOR_CREATE;
    static const MessageType VISITOR_CREATE_REPLY;
    static const MessageType VISITOR_DESTROY;
    static const MessageType VISITOR_DESTROY_REPLY;
    static const MessageType REQUESTBUCKETINFO;
    static const MessageType REQUESTBUCKETINFO_REPLY;
    static const MessageType NOTIFYBUCKETCHANGE;
    static const MessageType NOTIFYBUCKETCHANGE_REPLY;
    static const MessageType CREATEBUCKET;
    static const MessageType CREATEBUCKET_REPLY;
    static const MessageType MERGEBUCKET;
    static const MessageType MERGEBUCKET_REPLY;
    static const MessageType DELETEBUCKET;
    static const MessageType DELETEBUCKET_REPLY;
    static const MessageType SETNODESTATE;
    static const MessageType SETNODESTATE_REPLY;
    static const MessageType GETNODESTATE;
    static const MessageType GETNODESTATE_REPLY;
    static const MessageType SETSYSTEMSTATE;
    static const MessageType SETSYSTEMSTATE_REPLY;
    static const MessageType GETSYSTEMSTATE;
    static const MessageType GETSYSTEMSTATE_REPLY;
    static const MessageType ACTIVATE_CLUSTER_STATE_VERSION;
    static const MessageType ACTIVATE_CLUSTER_STATE_VERSION_REPLY;
    static const MessageType BUCKETSADDED;
    static const MessageType BUCKETSADDED_REPLY;
    static const MessageType BUCKETSREMOVED;
    static const MessageType BUCKETSREMOVED_REPLY;
    static const MessageType GETBUCKETDIFF;
    static const MessageType GETBUCKETDIFF_REPLY;
    static const MessageType APPLYBUCKETDIFF;
    static const MessageType APPLYBUCKETDIFF_REPLY;
    static const MessageType VISITOR_INFO;
    static const MessageType VISITOR_INFO_REPLY;
    static const MessageType SPLITBUCKET;
    static const MessageType SPLITBUCKET_REPLY;
    static const MessageType JOINBUCKETS;
    static const MessageType JOINBUCKETS_REPLY;
    static const MessageType MAPVISITOR;
    static const MessageType MAPVISITOR_REPLY;
    static const MessageType STATBUCKET;
    static const MessageType STATBUCKET_REPLY;
    static const MessageType GETBUCKETLIST;
    static const MessageType GETBUCKETLIST_REPLY;
    static const MessageType DOCUMENTLIST;
    static const MessageType DOCUMENTLIST_REPLY;
    static const MessageType UPDATE;
    static const MessageType UPDATE_REPLY;
    static const MessageType EMPTYBUCKETS;
    static const MessageType EMPTYBUCKETS_REPLY;
    static const MessageType REMOVELOCATION;
    static const MessageType REMOVELOCATION_REPLY;
    static const MessageType QUERYRESULT;
    static const MessageType QUERYRESULT_REPLY;
    static const MessageType SETBUCKETSTATE;
    static const MessageType SETBUCKETSTATE_REPLY;

    static const MessageType& get(Id id);

    MessageType(const MessageType &) = delete;
    MessageType& operator=(const MessageType &) = delete;
    ~MessageType();
    Id getId() const noexcept { return _id; }
    static Id getMaxId() noexcept { return MESSAGETYPE_MAX_ID; }
    const vespalib::string& getName() const noexcept { return _name; }
    bool isReply() const noexcept { return (_replyOf != 0); }
    /** Only valid to call on replies. */
    const MessageType& getCommandType() const noexcept { return *_replyOf; }
    /** Only valid to call on commands. */
    const MessageType& getReplyType() const noexcept { return *_reply; }
    bool operator==(const MessageType& type) const noexcept { return (_id == type._id); }
    bool operator!=(const MessageType& type) const noexcept { return (_id != type._id); }

    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

/**
 * Represent an address we can send a storage message to.
 * We have two kinds of addresses:
 * - A VDS address used to send to a single VDS node.
 * - An external mbus route, used to send to an external source.
 */
class StorageMessageAddress {
public:
    enum class Protocol : uint8_t { STORAGE, DOCUMENT };

private:
    const vespalib::string  *_cluster;
    // Used for internal VDS addresses only
    uint32_t                 _precomputed_storage_hash;
    lib::NodeType::Type      _type;
    Protocol                 _protocol;
    uint16_t                 _index;

public:
    StorageMessageAddress() noexcept; // Only to be used when transient default ctor semantics are needed by containers
    StorageMessageAddress(const vespalib::string * cluster, const lib::NodeType& type, uint16_t index) noexcept;
    StorageMessageAddress(const vespalib::string * cluster, const lib::NodeType& type, uint16_t index, Protocol protocol) noexcept;
    ~StorageMessageAddress();

    void setProtocol(Protocol p) noexcept { _protocol = p; }

    mbus::Route to_mbus_route() const;
    Protocol getProtocol() const noexcept { return _protocol; }
    uint16_t getIndex() const noexcept { return _index; }
    lib::NodeType::Type getNodeType() const noexcept { return _type; }
    const vespalib::string& getCluster() const noexcept { return *_cluster; }

    // Returns precomputed hash over <type, index> pair. Other fields not included.
    [[nodiscard]] uint32_t internal_storage_hash() const noexcept {
        return _precomputed_storage_hash;
    }

    bool operator==(const StorageMessageAddress& other) const noexcept;
    vespalib::string toString() const;
    friend std::ostream & operator << (std::ostream & os, const StorageMessageAddress & addr);
    static StorageMessageAddress create(const vespalib::string * cluster, const lib::NodeType& type, uint16_t index) noexcept {
        return api::StorageMessageAddress(cluster, type, index);
    }
    static StorageMessageAddress createDocApi(const vespalib::string * cluster, const lib::NodeType& type, uint16_t index) noexcept {
        return api::StorageMessageAddress(cluster, type, index, Protocol::DOCUMENT);
    }
private:
    void print(vespalib::asciistream & out) const;
};

struct TransportContext {
    virtual ~TransportContext() = 0;
};

enum class LockingRequirements : uint8_t {
    // Operations with exclusive locking can only be executed iff no other
    // exclusive or shared locks are taken for its bucket.
    Exclusive = 0,
    // Operations with shared locking can only be executed iff no exclusive
    // lock is taken for its bucket. Should only be used for read-only operations
    // that cannot mutate a bucket's state.
    Shared
};

const char* to_string(LockingRequirements req) noexcept;
std::ostream& operator<<(std::ostream&, LockingRequirements);

// This mirrors spi::ReadConsistency and has the same semantics, but is
// decoupled to avoid extra cross-module dependencies.
// Note that the name _internal_ read consistency is intentional to lessen
// any ambiguities on whether this is consistency in a distributed systems
// setting (i.e. linearizability) on internally in the persistence provider.
enum class InternalReadConsistency : uint8_t {
    Strong = 0,
    Weak
};

const char* to_string(InternalReadConsistency consistency) noexcept;
std::ostream& operator<<(std::ostream&, InternalReadConsistency);

class StorageMessage : public vespalib::Printable
{
    friend class StorageMessageTest; // Used for testing only
public:
    DECLARE_POINTER_TYPEDEFS(StorageMessage);
    using Id = uint64_t;
    using Priority = uint8_t;

    enum LegacyPriorityValues {
        LOW = 225,
        NORMAL = 127,
        HIGH = 50,
        VERYHIGH = 0
    }; // FIXME
    //static const unsigned int NUM_PRIORITIES = UINT8_MAX;
    static const char* getPriorityString(Priority);

private:
    static document::Bucket getDummyBucket() noexcept { return document::Bucket(document::BucketSpace::invalid(), document::BucketId()); }
    mutable std::unique_ptr<TransportContext> _transportContext;

protected:
    static Id generateMsgId() noexcept;

    const MessageType&    _type;
    Id                    _msgId;
    StorageMessageAddress _address;
    vespalib::Trace       _trace;
    uint32_t              _approxByteSize;
    Priority              _priority;

    StorageMessage(const MessageType& code, Id id) noexcept;
    StorageMessage(const StorageMessage&, Id id) noexcept;

public:
    StorageMessage& operator=(const StorageMessage&) = delete;
    StorageMessage(const StorageMessage&) = delete;
    ~StorageMessage() override;

    Id getMsgId() const noexcept { return _msgId; }

    /**
     * Set the id of this message. Typically used to set the id to a
     * unique value previously generated with the generateMsgId method.
     **/
    void forceMsgId(Id msgId) noexcept { _msgId = msgId; }

    const MessageType& getType() const noexcept { return _type; }

    void setPriority(Priority p) noexcept { _priority = p; }
    Priority getPriority() const noexcept { return _priority; }

    const StorageMessageAddress* getAddress() const noexcept { return (_address.getNodeType() != lib::NodeType::Type::UNKNOWN) ? &_address : nullptr; }

    void setAddress(const StorageMessageAddress& address) noexcept {
        _address = address;
    }

    /**
     *  Returns the approximate memory footprint (in bytes) of a storage message.
     *  By default, returns 50 bytes.
     */
    uint32_t getApproxByteSize() const noexcept {
        return _approxByteSize;
    }

    void setApproxByteSize(uint32_t value) noexcept {
        _approxByteSize = value;
    }

    /**
     * Used by storage to remember the context in which this message was
     * created, whether it was a storageprotocol message, a documentprotocol
     * message, or an RPC call.
     */
    void setTransportContext(std::unique_ptr<TransportContext> context) noexcept {
        _transportContext = std::move(context);
    }

    std::unique_ptr<TransportContext> getTransportContext() const noexcept {
        return std::move(_transportContext);
    }

    bool has_transport_context() const noexcept {
        return (_transportContext.get() != nullptr);
    }

    /**
     * This method is overloaded in subclasses and will call the correct
     * method in the MessageHandler interface.
     */
    virtual bool callHandler(MessageHandler&, const StorageMessage::SP&) const = 0;
    virtual bool hasTestAndSetCondition() const noexcept { return false; }

    mbus::Trace && steal_trace() noexcept { return std::move(_trace); }
    mbus::Trace& getTrace() noexcept { return _trace; }
    const mbus::Trace& getTrace() const noexcept { return _trace; }

    /**
       Sets the trace object for this message.
    */
    void setTrace(vespalib::Trace && trace) noexcept { _trace = std::move(trace); }

    /**
     * Cheap version of tostring().
     */
    virtual vespalib::string getSummary() const;

    virtual document::Bucket getBucket() const { return getDummyBucket(); }
    document::BucketId getBucketId() const noexcept { return getBucket().getBucketId(); }
    virtual LockingRequirements lockingRequirements() const noexcept {
        // Safe default: assume exclusive locking is required.
        return LockingRequirements::Exclusive;
    }
};

}
