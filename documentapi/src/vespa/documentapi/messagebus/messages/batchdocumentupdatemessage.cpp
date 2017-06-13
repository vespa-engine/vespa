// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "batchdocumentupdatemessage.h"
#include "batchdocumentupdatereply.h"
#include <vespa/documentapi/messagebus/documentprotocol.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vespalib/util/exceptions.h>

namespace documentapi {

BatchDocumentUpdateMessage::BatchDocumentUpdateMessage(uint64_t userId)
    : _userId(userId)
{
    setBucketId(document::UserDocIdString(vespalib::make_string("userdoc:foo:%lu:bar", _userId)));
}

BatchDocumentUpdateMessage::BatchDocumentUpdateMessage(const string& group)
    : _userId(0),
      _group(group)
{
    setBucketId(document::GroupDocIdString("groupdoc:foo:" + _group + ":bar"));
}

BatchDocumentUpdateMessage::~BatchDocumentUpdateMessage() {}

void
BatchDocumentUpdateMessage::setBucketId(const document::IdString& idString)
{
    document::BucketIdFactory factory;
    _bucketId = factory.getBucketId(document::DocumentId(idString));
}

void
BatchDocumentUpdateMessage::addUpdate(document::DocumentUpdate::SP update)
{
    verifyUpdate(*update);
    _updates.push_back(update);
}

void
BatchDocumentUpdateMessage::verifyUpdate(const document::DocumentUpdate& update) {
    const document::IdString& idString = update.getId().getScheme();

    if (_group.length()) {
        string group;

        if (idString.hasGroup()) {
            group = idString.getGroup();
        } else {
            throw vespalib::IllegalArgumentException("Batch update message can only contain groupdoc or orderdoc items");
        }

        if (group != _group) {
            throw vespalib::IllegalArgumentException(vespalib::make_string("Batch update message can not contain messages from group %s, only group %s", group.c_str(), _group.c_str()));
        }
    } else {
        uint64_t userId;

        if (idString.hasNumber()) {
            userId = idString.getNumber();
        } else {
            throw vespalib::IllegalArgumentException("Batch update message can only contain userdoc or orderdoc items");
        }

        if (userId != _userId) {
            throw vespalib::IllegalArgumentException(vespalib::make_string("Batch update message can not contain messages from user %llu, only user %llu", (long long unsigned)userId, (long long unsigned)_userId));
        }
    }
}

DocumentReply::UP
BatchDocumentUpdateMessage::doCreateReply() const
{
    return DocumentReply::UP(new BatchDocumentUpdateReply());
}

uint32_t
BatchDocumentUpdateMessage::getType() const {
    return DocumentProtocol::MESSAGE_BATCHDOCUMENTUPDATE;
}

}
