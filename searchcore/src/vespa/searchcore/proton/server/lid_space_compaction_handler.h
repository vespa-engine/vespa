// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "i_lid_space_compaction_handler.h"
#include "maintenancedocumentsubdb.h"

namespace proton {

class MaintenanceDocumentSubDB;

/**
 * Class that handles lid space compaction over a single document sub db.
 */
class LidSpaceCompactionHandler : public ILidSpaceCompactionHandler
{
private:
    const MaintenanceDocumentSubDB _subDb;
    const vespalib::string         _docTypeName;

public:
    LidSpaceCompactionHandler(const MaintenanceDocumentSubDB& subDb,
                              const vespalib::string& docTypeName);
    ~LidSpaceCompactionHandler() override;

    vespalib::string getName() const override;
    void set_operation_listener(std::shared_ptr<documentmetastore::OperationListener> op_listener) override;
    uint32_t getSubDbId() const override;
    search::LidUsageStats getLidStatus() const override;
    std::unique_ptr<IDocumentScanIterator> getIterator() const override;
    std::unique_ptr<MoveOperation> createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const override;
    void handleMove(const MoveOperation &op, std::shared_ptr<vespalib::IDestructorCallback> doneCtx) override;
    void handleCompactLidSpace(const CompactLidSpaceOperation &op, std::shared_ptr<vespalib::IDestructorCallback> compact_done_context) override;
    search::DocumentMetaData getMetaData(uint32_t lid) const override;
};

} // namespace proton

