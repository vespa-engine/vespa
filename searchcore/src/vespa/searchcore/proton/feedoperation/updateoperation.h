// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"
#include <vespa/document/update/documentupdate.h>

namespace proton {

class UpdateOperation : public DocumentOperation
{
private:
    document::DocumentUpdate::SP _upd;
    UpdateOperation(Type type,
                    const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const document::DocumentUpdate::SP &upd);
public:
    UpdateOperation();
    UpdateOperation(Type type);
    UpdateOperation(const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const document::DocumentUpdate::SP &upd);
    virtual ~UpdateOperation() {}
    const document::DocumentUpdate::SP &getUpdate() const { return _upd; }
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo) override;
    virtual vespalib::string toString() const override;
    static UpdateOperation makeOldUpdate(const document::BucketId &bucketId,
                                         const storage::spi::Timestamp &timestamp,
                                         const document::DocumentUpdate::SP &upd);
};

} // namespace proton

