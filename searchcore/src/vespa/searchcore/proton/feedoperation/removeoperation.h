// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"
#include <vespa/document/base/documentid.h>

namespace proton {

class RemoveOperation : public DocumentOperation {
    document::DocumentId _docId;

public:
    RemoveOperation();
    RemoveOperation(const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const document::DocumentId &docId);
    ~RemoveOperation() override {}
    const document::DocumentId &getDocumentId() const { return _docId; }
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo) override;
    virtual vespalib::string toString() const override;

    bool hasDocType() const { return _docId.hasDocType(); }
    vespalib::string getDocType() const { return _docId.getDocType(); }
};

} // namespace proton

