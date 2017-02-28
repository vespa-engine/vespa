// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentoperation.h"
#include <vespa/document/update/documentupdate.h>

namespace proton {

class UpdateOperation : public DocumentOperation
{
private:
    document::DocumentUpdate::SP _upd;
public:
    UpdateOperation();
    UpdateOperation(Type type);
    UpdateOperation(const document::BucketId &bucketId,
                    const storage::spi::Timestamp &timestamp,
                    const document::DocumentUpdate::SP &upd);
    virtual ~UpdateOperation() {}
    const document::DocumentUpdate::SP &getUpdate() const { return _upd; }
    virtual void serialize(vespalib::nbostream &os) const;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo);
    virtual vespalib::string toString() const;
};

} // namespace proton

