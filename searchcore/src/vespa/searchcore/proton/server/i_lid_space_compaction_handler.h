// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_document_scan_iterator.h"
#include "ifrozenbuckethandler.h"
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchlib/common/lid_usage_stats.h>

namespace vespalib { class IDestructorCallback; }

namespace proton::documentmetastore { class OperationListener; }

namespace proton {

/**
 * Interface for handling of lid space compaction, used by a LidSpaceCompactionJob.
 *
 * An implementation of this interface is typically working over a single document sub db.
 */
struct ILidSpaceCompactionHandler
{
    typedef std::unique_ptr<ILidSpaceCompactionHandler> UP;
    typedef std::vector<UP> Vector;

    virtual ~ILidSpaceCompactionHandler() {}

    /**
     * Returns the name of this handler.
     */
    virtual vespalib::string getName() const = 0;

    /**
     * Sets the listener used to get notifications on the operations handled by the document meta store.
     *
     * A call to this function should replace the previous listener if set.
     */
    virtual void set_operation_listener(std::shared_ptr<documentmetastore::OperationListener> op_listener) = 0;

    /**
     * Returns the id of the sub database this handler is operating over.
     */
    virtual uint32_t getSubDbId() const = 0;

    /**
     * Returns the current lid status of the underlying components.
     */
    virtual search::LidUsageStats getLidStatus() const = 0;

    /**
     * Returns an iterator for scanning documents.
     */
    virtual IDocumentScanIterator::UP getIterator() const = 0;

    /**
     * Creates a move operation for moving the given document to the given lid.
     */
    virtual MoveOperation::UP createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const = 0;

    /**
     * Performs the actual move operation.
     */
    virtual void handleMove(const MoveOperation &op, std::shared_ptr<vespalib::IDestructorCallback> moveDoneCtx) = 0;

    /**
     * Compacts the underlying lid space by starting using the new lid limit.
     */
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op, std::shared_ptr<vespalib::IDestructorCallback> compact_done_context) = 0;
};

} // namespace proton

