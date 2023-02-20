// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagemessage.h"
#include <vespa/messagebus/routing/verbatimdirective.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/stllike/hash_fun.h>
#include <sstream>
#include <cassert>
#include <atomic>

namespace storage::api {

namespace {

std::atomic<uint64_t> _G_lastMsgId(1000);

}

static const vespalib::string STORAGEADDRESS_PREFIX = "storage/cluster.";

const char*
StorageMessage::getPriorityString(Priority p) {
    switch (p) {
        case LOW: return "LOW";
        case NORMAL: return "NORMAL";
        case HIGH: return "HIGH";
        case VERYHIGH: return "VERYHIGH";
        default: return "UNKNOWN";
    }
}

std::map<MessageType::Id, MessageType*> MessageType::_codes;

const MessageType MessageType::DOCBLOCK("DocBlock", DOCBLOCK_ID);
const MessageType MessageType::DOCBLOCK_REPLY("DocBlock Reply", DOCBLOCK_REPLY_ID, &MessageType::DOCBLOCK);
const MessageType MessageType::GET("Get", GET_ID);
const MessageType MessageType::GET_REPLY("Get Reply", GET_REPLY_ID, &MessageType::GET);
const MessageType MessageType::INTERNAL("Internal", INTERNAL_ID);
const MessageType MessageType::INTERNAL_REPLY("Internal Reply", INTERNAL_REPLY_ID, &MessageType::INTERNAL);
const MessageType MessageType::PUT("Put", PUT_ID);
const MessageType MessageType::PUT_REPLY("Put Reply", PUT_REPLY_ID, &MessageType::PUT);
const MessageType MessageType::UPDATE("Update", UPDATE_ID);
const MessageType MessageType::UPDATE_REPLY("Update Reply", UPDATE_REPLY_ID, &MessageType::UPDATE);
const MessageType MessageType::REMOVE("Remove", REMOVE_ID);
const MessageType MessageType::REMOVE_REPLY("Remove Reply", REMOVE_REPLY_ID, &MessageType::REMOVE);
const MessageType MessageType::REVERT("Revert", REVERT_ID);
const MessageType MessageType::REVERT_REPLY("Revert Reply", REVERT_REPLY_ID, &MessageType::REVERT);
const MessageType MessageType::VISITOR_CREATE("Visitor Create", VISITOR_CREATE_ID);
const MessageType MessageType::VISITOR_CREATE_REPLY("Visitor Create Reply", VISITOR_CREATE_REPLY_ID, &MessageType::VISITOR_CREATE);
const MessageType MessageType::VISITOR_DESTROY("Visitor Destroy", VISITOR_DESTROY_ID);
const MessageType MessageType::VISITOR_DESTROY_REPLY("Visitor Destroy Reply", VISITOR_DESTROY_REPLY_ID, &MessageType::VISITOR_DESTROY);
const MessageType MessageType::REQUESTBUCKETINFO("Request bucket info", REQUESTBUCKETINFO_ID);
const MessageType MessageType::REQUESTBUCKETINFO_REPLY("Request bucket info reply", REQUESTBUCKETINFO_REPLY_ID, &MessageType::REQUESTBUCKETINFO);
const MessageType MessageType::NOTIFYBUCKETCHANGE("Notify bucket change", NOTIFYBUCKETCHANGE_ID);
const MessageType MessageType::NOTIFYBUCKETCHANGE_REPLY("Notify bucket change reply", NOTIFYBUCKETCHANGE_REPLY_ID, &MessageType::NOTIFYBUCKETCHANGE);
const MessageType MessageType::CREATEBUCKET("Create bucket", CREATEBUCKET_ID);
const MessageType MessageType::CREATEBUCKET_REPLY("Create bucket reply", CREATEBUCKET_REPLY_ID, &MessageType::CREATEBUCKET);
const MessageType MessageType::MERGEBUCKET("Merge bucket", MERGEBUCKET_ID);
const MessageType MessageType::MERGEBUCKET_REPLY("Merge bucket reply", MERGEBUCKET_REPLY_ID, &MessageType::MERGEBUCKET);
const MessageType MessageType::DELETEBUCKET("Delete bucket", DELETEBUCKET_ID);
const MessageType MessageType::DELETEBUCKET_REPLY("Delete bucket reply", DELETEBUCKET_REPLY_ID, &MessageType::DELETEBUCKET);
const MessageType MessageType::SETNODESTATE("Set node state", SETNODESTATE_ID);
const MessageType MessageType::SETNODESTATE_REPLY("Set node state reply", SETNODESTATE_REPLY_ID, &MessageType::SETNODESTATE);
const MessageType MessageType::GETNODESTATE("Get node state", GETNODESTATE_ID);
const MessageType MessageType::GETNODESTATE_REPLY("Get node state reply", GETNODESTATE_REPLY_ID, &MessageType::GETNODESTATE);
const MessageType MessageType::SETSYSTEMSTATE("Set system state", SETSYSTEMSTATE_ID);
const MessageType MessageType::SETSYSTEMSTATE_REPLY("Set system state reply", SETSYSTEMSTATE_REPLY_ID, &MessageType::SETSYSTEMSTATE);
const MessageType MessageType::GETSYSTEMSTATE("Get system state", GETSYSTEMSTATE_ID);
const MessageType MessageType::GETSYSTEMSTATE_REPLY("get system state reply", GETSYSTEMSTATE_REPLY_ID, &MessageType::GETSYSTEMSTATE);
const MessageType MessageType::ACTIVATE_CLUSTER_STATE_VERSION("Activate cluster state version", ACTIVATE_CLUSTER_STATE_VERSION_ID);
const MessageType MessageType::ACTIVATE_CLUSTER_STATE_VERSION_REPLY("Activate cluster state version reply", ACTIVATE_CLUSTER_STATE_VERSION_REPLY_ID, &MessageType::ACTIVATE_CLUSTER_STATE_VERSION);
const MessageType MessageType::GETBUCKETDIFF("GetBucketDiff", GETBUCKETDIFF_ID);
const MessageType MessageType::GETBUCKETDIFF_REPLY("GetBucketDiff reply", GETBUCKETDIFF_REPLY_ID, &MessageType::GETBUCKETDIFF);
const MessageType MessageType::APPLYBUCKETDIFF("ApplyBucketDiff", APPLYBUCKETDIFF_ID);
const MessageType MessageType::APPLYBUCKETDIFF_REPLY("ApplyBucketDiff reply", APPLYBUCKETDIFF_REPLY_ID, &MessageType::APPLYBUCKETDIFF);
const MessageType MessageType::VISITOR_INFO("VisitorInfo", VISITOR_INFO_ID);
const MessageType MessageType::VISITOR_INFO_REPLY("VisitorInfo reply", VISITOR_INFO_REPLY_ID, &MessageType::VISITOR_INFO);
const MessageType MessageType::SEARCHRESULT("SearchResult", SEARCHRESULT_ID);
const MessageType MessageType::SEARCHRESULT_REPLY("SearchResult reply", SEARCHRESULT_REPLY_ID, &MessageType::SEARCHRESULT);
const MessageType MessageType::DOCUMENTSUMMARY("DocumentSummary", DOCUMENTSUMMARY_ID);
const MessageType MessageType::DOCUMENTSUMMARY_REPLY("DocumentSummary reply", DOCUMENTSUMMARY_REPLY_ID, &MessageType::DOCUMENTSUMMARY);
const MessageType MessageType::MAPVISITOR("Mapvisitor", MAPVISITOR_ID);
const MessageType MessageType::MAPVISITOR_REPLY("Mapvisitor reply", MAPVISITOR_REPLY_ID, &MessageType::MAPVISITOR);
const MessageType MessageType::SPLITBUCKET("SplitBucket", SPLITBUCKET_ID);
const MessageType MessageType::SPLITBUCKET_REPLY("SplitBucket reply", SPLITBUCKET_REPLY_ID, &MessageType::SPLITBUCKET);
const MessageType MessageType::JOINBUCKETS("Joinbuckets", JOINBUCKETS_ID);
const MessageType MessageType::JOINBUCKETS_REPLY("Joinbuckets reply", JOINBUCKETS_REPLY_ID, &MessageType::JOINBUCKETS);
const MessageType MessageType::STATBUCKET("Statbucket", STATBUCKET_ID);
const MessageType MessageType::STATBUCKET_REPLY("Statbucket Reply", STATBUCKET_REPLY_ID, &MessageType::STATBUCKET);
const MessageType MessageType::GETBUCKETLIST("Getbucketlist", GETBUCKETLIST_ID);
const MessageType MessageType::GETBUCKETLIST_REPLY("Getbucketlist Reply", GETBUCKETLIST_REPLY_ID, &MessageType::GETBUCKETLIST);
const MessageType MessageType::DOCUMENTLIST("documentlist", DOCUMENTLIST_ID);
const MessageType MessageType::DOCUMENTLIST_REPLY("documentlist Reply", DOCUMENTLIST_REPLY_ID, &MessageType::DOCUMENTLIST);
const MessageType MessageType::EMPTYBUCKETS("Emptybuckets", EMPTYBUCKETS_ID);
const MessageType MessageType::EMPTYBUCKETS_REPLY("Emptybuckets Reply", EMPTYBUCKETS_REPLY_ID, &MessageType::EMPTYBUCKETS);
const MessageType MessageType::REMOVELOCATION("Removelocation", REMOVELOCATION_ID);
const MessageType MessageType::REMOVELOCATION_REPLY("Removelocation Reply", REMOVELOCATION_REPLY_ID, &MessageType::REMOVELOCATION);
const MessageType MessageType::QUERYRESULT("QueryResult", QUERYRESULT_ID);
const MessageType MessageType::QUERYRESULT_REPLY("QueryResult reply", QUERYRESULT_REPLY_ID, &MessageType::QUERYRESULT);
const MessageType MessageType::SETBUCKETSTATE("SetBucketState", SETBUCKETSTATE_ID);
const MessageType MessageType::SETBUCKETSTATE_REPLY("SetBucketStateReply", SETBUCKETSTATE_REPLY_ID, &MessageType::SETBUCKETSTATE);

const MessageType&
MessageType::MessageType::get(Id id)
{
    auto it = _codes.find(id);
    if (it == _codes.end()) {
        std::ostringstream ost;
        ost << "No message type with id " << id << ".";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    return *it->second;
}
MessageType::MessageType(vespalib::stringref name, Id id,
                         const MessageType* replyOf)
    : _name(name), _id(id), _reply(nullptr), _replyOf(replyOf)
{
    _codes[id] = this;
    if (_replyOf) {
        assert(_replyOf->_reply == nullptr);
        // Ugly cast to let initialization work
        auto& type = const_cast<MessageType&>(*_replyOf);
        type._reply = this;
    }
}

MessageType::~MessageType() = default;

void
MessageType::print(std::ostream& out, bool verbose, const std::string& indent) const
{
    (void) verbose; (void) indent;
    out << "MessageType(" << _id << ", " << _name;
    if (_replyOf) {
        out << ", reply of " << _replyOf->getName();
    }
    out << ")";
}

std::ostream & operator << (std::ostream & os, const StorageMessageAddress & addr) {
    return os << addr.toString();
}

namespace {

vespalib::string
createAddress(vespalib::stringref cluster, const lib::NodeType &type, uint16_t index) {
    vespalib::asciistream os;
    os << STORAGEADDRESS_PREFIX << cluster << '/' << type.toString() << '/' << index << "/default";
    return os.str();
}

uint32_t
calculate_node_hash(const lib::NodeType &type, uint16_t index) {
    uint16_t buf[] = {type, index};
    size_t hash =  vespalib::hashValue(&buf, sizeof(buf));
    return uint32_t(hash & 0xffffffffl) ^ uint32_t(hash >> 32);
}

vespalib::string Empty;

}

// TODO we ideally want this removed. Currently just in place to support usage as map key when emplacement not available
StorageMessageAddress::StorageMessageAddress() noexcept
    : _cluster(&Empty),
      _precomputed_storage_hash(0),
      _type(lib::NodeType::Type::UNKNOWN),
      _protocol(Protocol::STORAGE),
      _index(0)
{}

StorageMessageAddress::StorageMessageAddress(const vespalib::string * cluster, const lib::NodeType& type, uint16_t index) noexcept
    : StorageMessageAddress(cluster, type, index, Protocol::STORAGE)
{ }

StorageMessageAddress::StorageMessageAddress(const vespalib::string * cluster, const lib::NodeType& type,
                                             uint16_t index, Protocol protocol) noexcept
    : _cluster(cluster),
      _precomputed_storage_hash(calculate_node_hash(type, index)),
      _type(type.getType()),
      _protocol(protocol),
      _index(index)
{ }

StorageMessageAddress::~StorageMessageAddress() = default;

mbus::Route
StorageMessageAddress::to_mbus_route() const
{
    mbus::Route result;
    auto address_as_str = createAddress(getCluster(), lib::NodeType::get(_type), _index);
    std::vector<mbus::IHopDirective::SP> directives;
    directives.emplace_back(std::make_shared<mbus::VerbatimDirective>(std::move(address_as_str)));
    result.addHop(mbus::Hop(std::move(directives), false));
    return result;
}

bool
StorageMessageAddress::operator==(const StorageMessageAddress& other) const noexcept
{
    if (_protocol != other._protocol) return false;
    if (_type != other._type) return false;
    if (_index != other._index) return false;
    if (getCluster() != other.getCluster()) return false;
    return true;
}

vespalib::string
StorageMessageAddress::toString() const
{
    vespalib::asciistream os;
    print(os);
    return os.str();
}

void
StorageMessageAddress::print(vespalib::asciistream & out) const
{
    out << "StorageMessageAddress(";
    if (_protocol == Protocol::STORAGE) {
        out << "Storage protocol";
    } else {
        out << "Document protocol";
    }
    if (_type == lib::NodeType::Type::UNKNOWN) {
        out << ", " << to_mbus_route().toString() << ")";
    } else {
        out << ", cluster " << getCluster() << ", nodetype " << lib::NodeType::get(_type)
            << ", index " << _index << ")";
    }
}

TransportContext::~TransportContext() = default;

StorageMessage::Id
StorageMessage::generateMsgId() noexcept
{
    return _G_lastMsgId.fetch_add(1, std::memory_order_relaxed);
}

StorageMessage::StorageMessage(const MessageType& type, Id id) noexcept
    : _type(type),
      _msgId(id),
      _address(),
      _trace(),
      _approxByteSize(50),
      _priority(NORMAL)
{
}

StorageMessage::StorageMessage(const StorageMessage& other, Id id) noexcept
    : _type(other._type),
      _msgId(id),
      _address(),
      _trace(other.getTrace().getLevel()),
      _approxByteSize(other._approxByteSize),
      _priority(other._priority)
{
}

StorageMessage::~StorageMessage() = default;

vespalib::string
StorageMessage::getSummary() const {
    return toString();
}

const char*
to_string(LockingRequirements req) noexcept {
    switch (req) {
    case LockingRequirements::Exclusive: return "Exclusive";
    case LockingRequirements::Shared:    return "Shared";
    }
    abort();
}

std::ostream&
operator<<(std::ostream& os, LockingRequirements req) {
    os << to_string(req);
    return os;
}

const char*
to_string(InternalReadConsistency consistency) noexcept {
    switch (consistency) {
    case InternalReadConsistency::Strong: return "Strong";
    case InternalReadConsistency::Weak:   return "Weak";
    }
    abort();
}

std::ostream&
operator<<(std::ostream& os, InternalReadConsistency consistency) {
    os << to_string(consistency);
    return os;
}

}
