// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_lid_space_compaction_handler.h"
#include "maintenancedocumentsubdb.h"

namespace proton {

/**
 * Class that handles lid space compaction over a single document sub db.
 */
class LidSpaceCompactionHandler : public ILidSpaceCompactionHandler
{
private:
    const MaintenanceDocumentSubDB& _subDb;
    vespalib::string _docTypeName;

public:
    LidSpaceCompactionHandler(const MaintenanceDocumentSubDB& subDb,
                              const vespalib::string& docTypeName);

    // Implements ILidSpaceCompactionHandler
    virtual vespalib::string getName() const override {
        return _docTypeName + "." + _subDb.name();
    }
    virtual void set_operation_listener(std::shared_ptr<documentmetastore::OperationListener> op_listener) override;
    virtual uint32_t getSubDbId() const override { return _subDb.sub_db_id(); }
    virtual search::LidUsageStats getLidStatus() const override;
    virtual IDocumentScanIterator::UP getIterator() const override;
    virtual MoveOperation::UP createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const override;
    virtual void handleMove(const MoveOperation &op, std::shared_ptr<vespalib::IDestructorCallback> doneCtx) override;
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op, std::shared_ptr<vespalib::IDestructorCallback> compact_done_context) override;
};

} // namespace proton

