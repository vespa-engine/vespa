// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include "writedocumentreply.h"
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/bucket/bucketid.h>
#include <vespa/document/base/idstring.h>

namespace documentapi {

/**
   Message to use to send multiple updates for documents
   belonging to the same user or group to Vespa. Using this
   message improves performance in VDS mainly.
*/
class BatchDocumentUpdateMessage : public DocumentMessage
{
public:
    typedef std::vector<document::DocumentUpdate::SP > UpdateList;

    /**
       Creates a batch update message that can contain only updates
       for documents belonging to the given user.
    */
    BatchDocumentUpdateMessage(uint64_t userId);

    /**
       Creates a batch update message that can contain only updates
       for documents belonging to the given group.
    */
    BatchDocumentUpdateMessage(const string& group);

    /**
       @return Returns a list of the updates to be performed.
    */
    const UpdateList& getUpdates() const { return _updates; };

    /**
       Adds an update to be performed.
    */
    void addUpdate(document::DocumentUpdate::SP update);

    /**
       Returns the user id that this batch can contain.
       Only valid if this object was created with the first constructor.
    */
    uint64_t getUserId() const { return _userId; };

    /**
       Returns the grouo that this batch can contain.
       Only valid if this object was created with the second constructor.
    */
    const string& getGroup() const { return _group; }

    uint32_t getType() const override;

    /**
       Returns a bucket id suitable for routing this message.
    */
    const document::BucketId& getBucketId() const { return _bucketId; }

    string toString() const override { return "batchdocumentupdatemessage"; }

protected:
    DocumentReply::UP doCreateReply() const override;

private:
    uint64_t _userId;
    string _group;

    UpdateList _updates;
    document::BucketId _bucketId;

    void verifyUpdate(const document::DocumentUpdate& update);
    void setBucketId(const document::IdString& idString);
};

}

