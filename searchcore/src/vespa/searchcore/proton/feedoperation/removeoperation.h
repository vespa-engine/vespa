// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"

namespace proton {

class RemoveOperation : public DocumentOperation {
    document::DocumentId _docId;

public:
    RemoveOperation();
    RemoveOperation(const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const document::DocumentId &docId);
    RemoveOperation(const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const document::DocumentId &docId,
                    SerialNum serialNum,
                    DbDocumentId dbdId,
                    DbDocumentId prevDbdId);
    virtual ~RemoveOperation() {}
    const document::DocumentId &getDocumentId() const { return _docId; }
    virtual void serialize(vespalib::nbostream &os) const;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo);
    virtual vespalib::string toString() const;

    bool hasDocType() const { return _docId.hasDocType(); }
    vespalib::string getDocType() const { return _docId.getDocType(); }
};

} // namespace proton

