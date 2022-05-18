// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"

namespace proton {

class PutOperation : public DocumentOperation
{
    using DocumentSP = std::shared_ptr<document::Document>;
    DocumentSP _doc;

public:
    PutOperation();
    PutOperation(document::BucketId bucketId, Timestamp timestamp, DocumentSP doc);
    ~PutOperation() override;
    const DocumentSP &getDocument() const { return _doc; }
    void assertValid() const;
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    void deserializeDocument(const document::DocumentTypeRepo &repo);
    vespalib::string toString() const override;
};

} // namespace proton

