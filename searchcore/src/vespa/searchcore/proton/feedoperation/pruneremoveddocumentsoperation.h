// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "removedocumentsoperation.h"
#include <persistence/spi/types.h>
#include <vespa/searchlib/query/base.h>

namespace proton {

class PruneRemovedDocumentsOperation : public RemoveDocumentsOperation
{
private:
    uint32_t _subDbId;
public:
    typedef std::unique_ptr<PruneRemovedDocumentsOperation> UP;

    PruneRemovedDocumentsOperation();

    PruneRemovedDocumentsOperation(search::DocumentIdT docIdLimit,
                                   uint32_t subDbId);

    virtual
    ~PruneRemovedDocumentsOperation()
    {
    }

    uint32_t getSubDbId() const { return _subDbId; }

    void setLidsToRemove(const LidVectorContext::SP &lidsToRemove)
    {
        RemoveDocumentsOperation::setLidsToRemove(_subDbId, lidsToRemove);
    }

    const LidVectorContext::SP
    getLidsToRemove() const
    {
        return RemoveDocumentsOperation::getLidsToRemove(_subDbId);
    }

    virtual void
    serialize(vespalib::nbostream &os) const override;

    virtual void
    deserialize(vespalib::nbostream &is,
                const document::DocumentTypeRepo &repo) override;

    virtual vespalib::string toString() const override;
};

} // namespace proton

