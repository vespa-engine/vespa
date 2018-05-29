// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"

namespace proton {

class PutOperation : public DocumentOperation
{
    using DocumentSP = std::shared_ptr<document::Document>;
    DocumentSP _doc;

public:
    PutOperation();
    PutOperation(const document::BucketId &bucketId,
                 const storage::spi::Timestamp &timestamp,
                 const DocumentSP &doc);
    virtual ~PutOperation();
    const DocumentSP &getDocument() const { return _doc; }
    void assertValid() const;
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo) override;
    void deserializeDocument(const document::DocumentTypeRepo &repo);
    virtual vespalib::string toString() const override;
};

} // namespace proton

