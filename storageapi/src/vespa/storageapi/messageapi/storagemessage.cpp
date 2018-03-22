// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "storagemessage.h"

#include <vespa/messagebus/routing/verbatimdirective.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <sstream>
#include <cassert>

namespace storage::api {

namespace {

/**
 * TODO
 * From @vekterli
 * I have no idea why the _lastMsgId update code masks away the 8 MSB, but if we assume it's probably for no
 * overwhelmingly good reason we could replace this mutex with just a std::atomic<uint64_t> and do a relaxed
 * fetch_add (shouldn't be any need for any barriers; ID increments have no other memory dependencies). U64 overflows
 * here come under the category "never gonna happen in the real world".
 * @balder agree - @vekterli fix in separate pull request :)
 */
vespalib::Lock _G_msgIdLock;

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
const MessageType MessageType::DOCBLOCK_REPLY(
        "DocBlock Reply", DOCBLOCK_REPLY_ID, &MessageType::DOCBLOCK);
const MessageType MessageType::GET("Get", GET_ID);
const MessageType MessageType::GET_REPLY(
        "Get Reply", GET_REPLY_ID, &MessageType::GET);
const MessageType MessageType::INTERNAL("Internal", INTERNAL_ID);
const MessageType MessageType::INTERNAL_REPLY(
        "Internal Reply", INTERNAL_REPLY_ID, &MessageType::INTERNAL);
const MessageType MessageType::PUT("Put", PUT_ID);
const MessageType MessageType::PUT_REPLY(
        "Put Reply", PUT_REPLY_ID, &MessageType::PUT);
const MessageType MessageType::UPDATE("Update", UPDATE_ID);
const MessageType MessageType::UPDATE_REPLY(
        "Update Reply", UPDATE_REPLY_ID, &MessageType::UPDATE);
const MessageType MessageType::REMOVE("Remove", REMOVE_ID);
const MessageType MessageType::REMOVE_REPLY(
        "Remove Reply", REMOVE_REPLY_ID, &MessageType::REMOVE);
const MessageType MessageType::REVERT("Revert", REVERT_ID);
const MessageType MessageType::REVERT_REPLY(
        "Revert Reply", REVERT_REPLY_ID, &MessageType::REVERT);
const MessageType MessageType::VISITOR_CREATE(
        "Visitor Create", VISITOR_CREATE_ID);
const MessageType MessageType::VISITOR_CREATE_REPLY(
        "Visitor Create Reply", VISITOR_CREATE_REPLY_ID,
        &MessageType::VISITOR_CREATE);
const MessageType MessageType::VISITOR_DESTROY(
        "Visitor Destroy", VISITOR_DESTROY_ID);
const MessageType MessageType::VISITOR_DESTROY_REPLY(
        "Visitor Destroy Reply", VISITOR_DESTROY_REPLY_ID,
        &MessageType::VISITOR_DESTROY);
const MessageType MessageType::REQUESTBUCKETINFO("Request bucket info",
        REQUESTBUCKETINFO_ID);
const MessageType MessageType::REQUESTBUCKETINFO_REPLY(
        "Request bucket info reply", REQUESTBUCKETINFO_REPLY_ID,
        &MessageType::REQUESTBUCKETINFO);
const MessageType MessageType::NOTIFYBUCKETCHANGE("Notify bucket change",
        NOTIFYBUCKETCHANGE_ID);
const MessageType MessageType::NOTIFYBUCKETCHANGE_REPLY(
        "Notify bucket change reply", NOTIFYBUCKETCHANGE_REPLY_ID,
        &MessageType::NOTIFYBUCKETCHANGE);
const MessageType MessageType::CREATEBUCKET("Create bucket", CREATEBUCKET_ID);
const MessageType MessageType::CREATEBUCKET_REPLY(
        "Create bucket reply", CREATEBUCKET_REPLY_ID,
        &MessageType::CREATEBUCKET);
const MessageType MessageType::MERGEBUCKET("Merge bucket", MERGEBUCKET_ID);
const MessageType MessageType::MERGEBUCKET_REPLY(
        "Merge bucket reply", MERGEBUCKET_REPLY_ID,
        &MessageType::MERGEBUCKET);
const MessageType MessageType::DELETEBUCKET("Delete bucket", DELETEBUCKET_ID);
const MessageType MessageType::DELETEBUCKET_REPLY(
        "Delete bucket reply", DELETEBUCKET_REPLY_ID,
        &MessageType::DELETEBUCKET);
const MessageType MessageType::SETNODESTATE("Set node state", SETNODESTATE_ID);
const MessageType MessageType::SETNODESTATE_REPLY(
        "Set node state reply", SETNODESTATE_REPLY_ID,
        &MessageType::SETNODESTATE);
const MessageType MessageType::GETNODESTATE("Get node state", GETNODESTATE_ID);
const MessageType MessageType::GETNODESTATE_REPLY(
        "Get node state reply", GETNODESTATE_REPLY_ID,
        &MessageType::GETNODESTATE);
const MessageType MessageType::SETSYSTEMSTATE("Set system state", SETSYSTEMSTATE_ID);
const MessageType MessageType::SETSYSTEMSTATE_REPLY(
        "Set system state reply", SETSYSTEMSTATE_REPLY_ID,
        &MessageType::SETSYSTEMSTATE);
const MessageType MessageType::GETSYSTEMSTATE("Get system state", GETSYSTEMSTATE_ID);
const MessageType MessageType::GETSYSTEMSTATE_REPLY(
        "get system state reply", GETSYSTEMSTATE_REPLY_ID,
        &MessageType::GETSYSTEMSTATE);
const MessageType MessageType::GETBUCKETDIFF("GetBucketDiff", GETBUCKETDIFF_ID);
const MessageType MessageType::GETBUCKETDIFF_REPLY(
        "GetBucketDiff reply", GETBUCKETDIFF_REPLY_ID,
        &MessageType::GETBUCKETDIFF);
const MessageType MessageType::APPLYBUCKETDIFF("ApplyBucketDiff",
        APPLYBUCKETDIFF_ID);
const MessageType MessageType::APPLYBUCKETDIFF_REPLY(
        "ApplyBucketDiff reply", APPLYBUCKETDIFF_REPLY_ID,
        &MessageType::APPLYBUCKETDIFF);
const MessageType MessageType::VISITOR_INFO("VisitorInfo",
        VISITOR_INFO_ID);
const MessageType MessageType::VISITOR_INFO_REPLY(
        "VisitorInfo reply", VISITOR_INFO_REPLY_ID,
        &MessageType::VISITOR_INFO);
const MessageType MessageType::SEARCHRESULT("SearchResult", SEARCHRESULT_ID);
const MessageType MessageType::SEARCHRESULT_REPLY(
        "SearchResult reply", SEARCHRESULT_REPLY_ID,
        &MessageType::SEARCHRESULT);
const MessageType MessageType::DOCUMENTSUMMARY("DocumentSummary", DOCUMENTSUMMARY_ID);
const MessageType MessageType::DOCUMENTSUMMARY_REPLY(
        "DocumentSummary reply", DOCUMENTSUMMARY_REPLY_ID,
        &MessageType::DOCUMENTSUMMARY);
const MessageType MessageType::MAPVISITOR("Mapvisitor", MAPVISITOR_ID);
const MessageType MessageType::MAPVISITOR_REPLY(
        "Mapvisitor reply", MAPVISITOR_REPLY_ID,
        &MessageType::MAPVISITOR);
const MessageType MessageType::SPLITBUCKET("SplitBucket", SPLITBUCKET_ID);
const MessageType MessageType::SPLITBUCKET_REPLY(
        "SplitBucket reply", SPLITBUCKET_REPLY_ID,
        &MessageType::SPLITBUCKET);
const MessageType MessageType::JOINBUCKETS("Joinbuckets", JOINBUCKETS_ID);
const MessageType MessageType::JOINBUCKETS_REPLY(
        "Joinbuckets reply", JOINBUCKETS_REPLY_ID,
        &MessageType::JOINBUCKETS);
const MessageType MessageType::STATBUCKET("Statbucket", STATBUCKET_ID);
const MessageType MessageType::STATBUCKET_REPLY(
        "Statbucket Reply", STATBUCKET_REPLY_ID, &MessageType::STATBUCKET);
const MessageType MessageType::GETBUCKETLIST("Getbucketlist", GETBUCKETLIST_ID);
const MessageType MessageType::GETBUCKETLIST_REPLY(
        "Getbucketlist Reply", GETBUCKETLIST_REPLY_ID, &MessageType::GETBUCKETLIST);
const MessageType MessageType::DOCUMENTLIST("documentlist", DOCUMENTLIST_ID);
const MessageType MessageType::DOCUMENTLIST_REPLY(
        "documentlist Reply", DOCUMENTLIST_REPLY_ID, &MessageType::DOCUMENTLIST);
const MessageType MessageType::EMPTYBUCKETS("Emptybuckets", EMPTYBUCKETS_ID);
const MessageType MessageType::EMPTYBUCKETS_REPLY(
        "Emptybuckets Reply", EMPTYBUCKETS_REPLY_ID, &MessageType::EMPTYBUCKETS);
const MessageType MessageType::REMOVELOCATION("Removelocation", REMOVELOCATION_ID);
const MessageType MessageType::REMOVELOCATION_REPLY(
        "Removelocation Reply", REMOVELOCATION_REPLY_ID, &MessageType::REMOVELOCATION);
const MessageType MessageType::QUERYRESULT("QueryResult", QUERYRESULT_ID);
const MessageType MessageType::QUERYRESULT_REPLY(
        "QueryResult reply", QUERYRESULT_REPLY_ID,
        &MessageType::QUERYRESULT);
const MessageType MessageType::BATCHPUTREMOVE("BatchPutRemove", BATCHPUTREMOVE_ID);
const MessageType MessageType::BATCHPUTREMOVE_REPLY(
        "BatchPutRemove reply", BATCHPUTREMOVE_REPLY_ID,
        &MessageType::BATCHPUTREMOVE);
const MessageType MessageType::BATCHDOCUMENTUPDATE("BatchDocumentUpdate", BATCHDOCUMENTUPDATE_ID);
const MessageType MessageType::BATCHDOCUMENTUPDATE_REPLY(
        "BatchDocumentUpdate reply", BATCHDOCUMENTUPDATE_REPLY_ID,
        &MessageType::BATCHDOCUMENTUPDATE);
const MessageType MessageType::SETBUCKETSTATE(
        "SetBucketState",
        SETBUCKETSTATE_ID);
const MessageType MessageType::SETBUCKETSTATE_REPLY(
        "SetBucketStateReply",
        SETBUCKETSTATE_REPLY_ID,
        &MessageType::SETBUCKETSTATE);

const MessageType&
MessageType::MessageType::get(Id id)
{
    std::map<Id, MessageType*>::const_iterator it = _codes.find(id);
    if (it == _codes.end()) {
        std::ostringstream ost;
        ost << "No message type with id " << id << ".";
        throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
    }
    return *it->second;
}
MessageType::MessageType(const vespalib::stringref & name, Id id,
            const MessageType* replyOf)
        : _name(name), _id(id), _reply(NULL), _replyOf(replyOf)
{
    _codes[id] = this;
    if (_replyOf != 0) {
        assert(_replyOf->_reply == 0);
        // Ugly cast to let initialization work
        MessageType& type = const_cast<MessageType&>(*_replyOf);
        type._reply = this;
    }
}

MessageType::~MessageType() {}

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

StorageMessageAddress::StorageMessageAddress(const mbus::Route& route)
    : _route(route),
      _retryEnabled(false),
      _protocol(DOCUMENT),
      _cluster(""),
      _type(0),
      _index(0xFFFF)
{ }

std::ostream & operator << (std::ostream & os, const StorageMessageAddress & addr) {
    return os << addr.toString();
}

static vespalib::string
createAddress(const vespalib::stringref & cluster, const lib::NodeType& type, uint16_t index)
{
    vespalib::asciistream os;
    os << STORAGEADDRESS_PREFIX << cluster << '/' << type.toString() << '/' << index << "/default";
    return os.str();
}

StorageMessageAddress::StorageMessageAddress(const vespalib::stringref & cluster, const lib::NodeType& type,
                                             uint16_t index, Protocol protocol)
    : _route(),
      _retryEnabled(false),
      _protocol(protocol),
      _cluster(cluster),
      _type(&type),
      _index(index)
{
    std::vector<mbus::IHopDirective::SP> directives;
    directives.emplace_back(std::make_shared<mbus::VerbatimDirective>(createAddress(cluster, type, index)));
    _route.addHop(mbus::Hop(std::move(directives), false));
}

StorageMessageAddress::~StorageMessageAddress() = default;

uint16_t
StorageMessageAddress::getIndex() const
{
    if (_type == 0) {
        throw vespalib::IllegalStateException(
                "Cannot retrieve node index out of external address",
                VESPA_STRLOC);
    }
    return _index;
}

const lib::NodeType&
StorageMessageAddress::getNodeType() const
{
    if (_type == 0) {
        throw vespalib::IllegalStateException(
                "Cannot retrieve node type out of external address",
                VESPA_STRLOC);
    }
    return *_type;
}

const vespalib::string&
StorageMessageAddress::getCluster() const
{
    if (_type == 0) {
        throw vespalib::IllegalStateException(
                "Cannot retrieve cluster out of external address",
                VESPA_STRLOC);
    }
    return _cluster;
}

bool
StorageMessageAddress::operator==(const StorageMessageAddress& other) const
{
    if (_protocol != other._protocol) return false;
    if (_retryEnabled != other._retryEnabled) return false;
    if (_type != other._type) return false;
    if (_type != 0) {
        if (_cluster != other._cluster) return false;
        if (_index != other._index) return false;
        if (_type != other._type) return false;
    }
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
    if (_protocol == STORAGE) {
        out << "Storage protocol";
    } else {
        out << "Document protocol";
    }
    if (_retryEnabled) {
        out << ", retry enabled";
    }
    if (_type == 0) {
        out << ", " << _route.toString() << ")";
    } else {
        out << ", cluster " << _cluster << ", nodetype " << *_type
            << ", index " << _index << ")";
    }
}

TransportContext::~TransportContext() = default;

StorageMessage::Id StorageMessage::_lastMsgId = 1000;

StorageMessage::Id
StorageMessage::generateMsgId()
{
    vespalib::LockGuard sync(_G_msgIdLock);
    Id msgId = _lastMsgId++;
    _lastMsgId &= ((Id(-1) << 8) >> 8);
    return msgId;
}

StorageMessage::StorageMessage(const MessageType& type, Id id)
    : _type(type),
      _msgId(id),
      _priority(NORMAL),
      _address(),
      _loadType(documentapi::LoadType::DEFAULT)
{
}

StorageMessage::StorageMessage(const StorageMessage& other, Id id)
    : _type(other._type),
      _msgId(id),
      _priority(other._priority),
      _address(),
      _loadType(other._loadType)
{
}

StorageMessage::~StorageMessage() { }

void StorageMessage::setNewMsgId()
{
    vespalib::LockGuard sync(_G_msgIdLock);
    _msgId = _lastMsgId++;
    _lastMsgId &= ((Id(-1) << 8) >> 8);
}

vespalib::string
StorageMessage::getSummary() const {
    return toString();
}

}
