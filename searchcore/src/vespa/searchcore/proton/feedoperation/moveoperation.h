// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"

namespace proton {

class MoveOperation : public DocumentOperation
{
private:
    using DocumentSP = std::shared_ptr<document::Document>;
    DocumentSP _doc;
public:
    typedef std::unique_ptr<MoveOperation> UP;

    MoveOperation();
    MoveOperation(const document::BucketId &bucketId,
                  const storage::spi::Timestamp &timestamp,
                  const DocumentSP &doc,
                  DbDocumentId sourceDbdId,
                  uint32_t targetSubDbId);
    ~MoveOperation() override;
    const DocumentSP &getDocument() const { return _doc; }
    DbDocumentId getSourceDbdId() const { return getPrevDbDocumentId(); }
    DbDocumentId getTargetDbdId() const { return getDbDocumentId(); }
    void setTargetLid(search::DocumentIdT lid) {
        setDbDocumentId(DbDocumentId(getSubDbId(), lid));
    }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    vespalib::string toString() const override;
};

} // namespace proton

