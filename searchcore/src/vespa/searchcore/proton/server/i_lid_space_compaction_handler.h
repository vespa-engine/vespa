// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/lid_usage_stats.h>
#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vector>

namespace vespalib { class IDestructorCallback; }
namespace search { struct DocumentMetaData; }
namespace proton::documentmetastore { class OperationListener; }

namespace proton {

class MoveOperation;
class CompactLidSpaceOperation;
struct IDocumentScanIterator;

/**
 * Interface for handling of lid space compaction, used by a LidSpaceCompactionJob.
 *
 * An implementation of this interface is typically working over a single document sub db.
 */
struct ILidSpaceCompactionHandler
{
    typedef std::shared_ptr<ILidSpaceCompactionHandler> SP;
    using Vector = std::vector<SP>;

    virtual ~ILidSpaceCompactionHandler() = default;

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
    virtual std::unique_ptr<IDocumentScanIterator> getIterator() const = 0;

    /**
     * Return the meta data associated with the given lid
     */
    virtual search::DocumentMetaData getMetaData(uint32_t lid) const = 0;

    /**
     * Creates a move operation for moving the given document to the given lid.
     */
    virtual std::unique_ptr<MoveOperation> createMoveOperation(const search::DocumentMetaData &document, uint32_t moveToLid) const = 0;

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
