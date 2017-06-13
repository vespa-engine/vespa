// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "messageallocationtypes.h"
#include <vespa/storageapi/messageapi/storagemessage.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace storage {

MessageAllocationTypes::MessageAllocationTypes(framework::MemoryManagerInterface& manager)
{
    using api::MessageType;
    using framework::MemoryAllocationType;

    _types.resize(MessageType::MESSAGETYPE_MAX_ID);
    _types[MessageType::DOCBLOCK_ID] = &manager.registerAllocationType(MemoryAllocationType("MESSAGE_DOCBLOCK"));
    _types[MessageType::DOCBLOCK_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MESSAGE_DOCBLOCK_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GET_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GET", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::GET_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GET_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::INTERNAL_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::INTERNAL"));
    _types[MessageType::INTERNAL_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::INTERNAL_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::PUT_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::PUT", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::PUT_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::PUT_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::REMOVE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REMOVE", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::REMOVE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REMOVE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::REVERT_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REVERT"));
    _types[MessageType::REVERT_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REVERT_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::VISITOR_CREATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::VISITOR_CREATE", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::VISITOR_CREATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::VISITOR_CREATE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::VISITOR_DESTROY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::VISITOR_DESTROY"));
    _types[MessageType::VISITOR_DESTROY_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::VISITOR_DESTROY_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::REQUESTBUCKETINFO_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REQUESTBUCKETINFO"));
    _types[MessageType::REQUESTBUCKETINFO_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REQUESTBUCKETINFO_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::NOTIFYBUCKETCHANGE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::NOTIFYBUCKETCHANGE"));
    _types[MessageType::NOTIFYBUCKETCHANGE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::NOTIFYBUCKETCHANGE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::CREATEBUCKET_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::CREATEBUCKET"));
    _types[MessageType::CREATEBUCKET_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::CREATEBUCKET_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::MERGEBUCKET_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::MERGEBUCKET"));
    _types[MessageType::MERGEBUCKET_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::MERGEBUCKET_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::DELETEBUCKET_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::DELETEBUCKET"));
    _types[MessageType::DELETEBUCKET_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::DELETEBUCKET_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::SETNODESTATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SETNODESTATE", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::SETNODESTATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SETNODESTATE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GETNODESTATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETNODESTATE", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GETNODESTATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETNODESTATE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::SETSYSTEMSTATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SETSYSTEMSTATE", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::SETSYSTEMSTATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SETSYSTEMSTATE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GETSYSTEMSTATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETSYSTEMSTATE", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GETSYSTEMSTATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETSYSTEMSTATE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GETBUCKETDIFF_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETBUCKETDIFF", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GETBUCKETDIFF_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETBUCKETDIFF_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::APPLYBUCKETDIFF_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::APPLYBUCKETDIFF", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::APPLYBUCKETDIFF_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::APPLYBUCKETDIFF_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::VISITOR_INFO_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::VISITOR_INFO"));
    _types[MessageType::VISITOR_INFO_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::VISITOR_INFO_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::SEARCHRESULT_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SEARCHRESULT"));
    _types[MessageType::SEARCHRESULT_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SEARCHRESULT_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::SPLITBUCKET_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SPLITBUCKET"));
    _types[MessageType::SPLITBUCKET_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SPLITBUCKET_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::JOINBUCKETS_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::JOINBUCKETS"));
    _types[MessageType::JOINBUCKETS_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::JOINBUCKETS_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::SETBUCKETSTATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SETBUCKETSTATE"));
    _types[MessageType::SETBUCKETSTATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::SETBUCKETSTATE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::MULTIOPERATION_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::MULTIOPERATION", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::MULTIOPERATION_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::MULTIOPERATION_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::DOCUMENTSUMMARY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::DOCUMENTSUMMARY"));
    _types[MessageType::DOCUMENTSUMMARY_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::DOCUMENTSUMMARY_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::MAPVISITOR_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::MAPVISITOR"));
    _types[MessageType::MAPVISITOR_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::MAPVISITOR_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::STATBUCKET_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::STATBUCKET", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::STATBUCKET_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::STATBUCKET_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::GETBUCKETLIST_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETBUCKETLIST", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::GETBUCKETLIST_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::GETBUCKETLIST_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::DOCUMENTLIST_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::DOCUMENTLIST"));
    _types[MessageType::DOCUMENTLIST_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::DOCUMENTLIST_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::UPDATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::UPDATE", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::UPDATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::UPDATE_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::EMPTYBUCKETS_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::EMPTYBUCKETS"));
    _types[MessageType::EMPTYBUCKETS_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::EMPTYBUCKETS_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::REMOVELOCATION_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REMOVELOCATION", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::REMOVELOCATION_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::REMOVELOCATION_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::QUERYRESULT_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::QUERYRESULT"));
    _types[MessageType::QUERYRESULT_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::QUERYRESULT_REPLY", framework::MemoryAllocationType::FORCE_ALLOCATE));
    _types[MessageType::BATCHPUTREMOVE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::BATCHPUTREMOVE", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::BATCHPUTREMOVE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::BATCHPUTREMOVE_REPLY", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::BATCHDOCUMENTUPDATE_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::BATCHDOCUMENTUPDATE", framework::MemoryAllocationType::EXTERNAL_LOAD));
    _types[MessageType::BATCHDOCUMENTUPDATE_REPLY_ID] = &manager.registerAllocationType(MemoryAllocationType("MessageType::BATCHDOCUMENTUPDATE_REPLY", framework::MemoryAllocationType::EXTERNAL_LOAD));
}

const framework::MemoryAllocationType&
MessageAllocationTypes::getType(uint32_t type) const {
    if (_types.size() > size_t(type) && _types[type] != 0) {
        return *_types[type];
    }
    vespalib::asciistream ost;
    ost << "No type registered with value " << type << ".";
    throw vespalib::IllegalArgumentException(ost.str(), VESPA_STRLOC);
}

} // storage
