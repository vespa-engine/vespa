// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"

namespace document {
class DocumentTypeRepo;
class DocumentUpdate;
}

namespace proton {

class UpdateOperation : public DocumentOperation
{
private:
    using DocumentUpdateSP = std::shared_ptr<document::DocumentUpdate>;
    DocumentUpdateSP _upd;
    UpdateOperation(Type type,
                    const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const DocumentUpdateSP &upd);
    void serializeUpdate(vespalib::nbostream &os) const;
    void deserializeUpdate(vespalib::nbostream &is, const document::DocumentTypeRepo &repo);
public:
    UpdateOperation();
    UpdateOperation(Type type);
    UpdateOperation(const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const DocumentUpdateSP &upd);
    virtual ~UpdateOperation() {}
    const DocumentUpdateSP &getUpdate() const { return _upd; }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    void deserializeUpdate(const document::DocumentTypeRepo &repo);
    virtual vespalib::string toString() const override;
    static UpdateOperation makeOldUpdate(const document::BucketId &bucketId,
                                         const storage::spi::Timestamp &timestamp,
                                         const DocumentUpdateSP &upd);
};

} // namespace proton
