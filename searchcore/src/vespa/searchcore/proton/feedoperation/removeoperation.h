// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"
#include <vespa/document/base/documentid.h>

namespace proton {

class RemoveOperation : public DocumentOperation {
protected:
    explicit RemoveOperation(Type type) : DocumentOperation(type) {}
    RemoveOperation(Type type, document::BucketId bucketId, Timestamp timestamp)
        : DocumentOperation(type, bucketId, timestamp)
    {}
public:
    virtual bool hasDocType() const = 0;
    virtual vespalib::stringref getDocType() const = 0;
    virtual const document::GlobalId & getGlobalId() const = 0;
};

class RemoveOperationWithDocId : public RemoveOperation {
    document::DocumentId _docId;

public:
    RemoveOperationWithDocId();
    RemoveOperationWithDocId(document::BucketId bucketId, Timestamp timestamp, const document::DocumentId &docId);
    ~RemoveOperationWithDocId() override;
    const document::DocumentId &getDocumentId() const { return _docId; }
    const document::GlobalId & getGlobalId() const override { return _docId.getGlobalId(); }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    vespalib::string toString() const override;

    bool hasDocType() const override { return _docId.hasDocType(); }
    vespalib::stringref getDocType() const override { return _docId.getDocType(); }
};

class RemoveOperationWithGid : public RemoveOperation {
    document::GlobalId _gid;
    vespalib::string   _docType;

public:
    RemoveOperationWithGid();
    RemoveOperationWithGid(document::BucketId bucketId, Timestamp timestamp,
                           const document::GlobalId & gid, vespalib::stringref docType);
    ~RemoveOperationWithGid() override;
    const document::GlobalId & getGlobalId() const override { return _gid; }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    vespalib::string toString() const override;

    bool hasDocType() const override { return true; }
    vespalib::stringref getDocType() const override { return _docType; }
};

} // namespace proton

