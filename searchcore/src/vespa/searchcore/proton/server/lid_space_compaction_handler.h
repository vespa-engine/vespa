// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_lid_space_compaction_handler.h"
#include "idocumentsubdb.h"

namespace proton {

/**
 * Class that handles lid space compaction over a single document sub db.
 */
class LidSpaceCompactionHandler : public ILidSpaceCompactionHandler
{
private:
    IDocumentSubDB  &_subDb;
    vespalib::string _docTypeName;

public:
    LidSpaceCompactionHandler(IDocumentSubDB &subDb,
                              const vespalib::string &docTypeName);

    // Implements ILidSpaceCompactionHandler
    virtual vespalib::string getName() const override {
        return _docTypeName + "." + _subDb.getName();
    }
    virtual uint32_t getSubDbId() const override { return _subDb.getSubDbId(); }
    virtual search::LidUsageStats getLidStatus() const override;
    virtual IDocumentScanIterator::UP getIterator() const override;
    virtual MoveOperation::UP createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const override;
    virtual void handleMove(const MoveOperation &op, std::shared_ptr<search::IDestructorCallback> doneCtx) override;
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op) override;
};

} // namespace proton

