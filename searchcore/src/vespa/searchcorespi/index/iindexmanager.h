// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexsearchable.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcorespi/flush/flushstats.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/serialnum.h>

namespace vespalib { class IDestructorCallback; }
namespace document { class Document; }

namespace searchcorespi {

/**
 * Interface for an index manager:
 *  - Keeps track of a set of indexes (i.e. both memory indexes and disk indexes).
 *  - Documents can be inserted, updated or removed in/from the active memory index.
 *  - Enables search across all the indexes.
 *  - Manages the set of indexes through flush targets to the flush engine (i.e. flushing of memory
 *    indexes and fusion of disk indexes).
 *
 * Key items in this interface is <em>lid</em> which is a local document id assigned to each document.
 * This is a numeric id in the range [0...Maximum number of documents concurrently in this DB>.
 * Local document id 0 is reserved. Another key item is the <em>serialnumber</em> which is the
 * serial number an operation was given when first seen by the searchcore. This is a monotonic
 * increasing number used for sequencing operations and figuring out how up to date componets are.
 * Used during restart/replay and for deciding when to flush. Both the lid and the serial number
 * are persisted alongside an operation to ensure correct playback during recovery.
 */
class IIndexManager {
protected:
    using Document = document::Document;
    using SerialNum = search::SerialNum;
    using Schema = search::index::Schema;
    using LidVector = std::vector<uint32_t>;
public:
    using OnWriteDoneType = std::shared_ptr<vespalib::IDestructorCallback>;

    struct Configure {
        virtual ~Configure() = default;
        virtual bool configure() = 0;
    };
    template <class FunctionType>
    class LambdaConfigure : public Configure {
        FunctionType _func;

    public:
        LambdaConfigure(FunctionType &&func)
            : _func(std::move(func))
        {}
        ~LambdaConfigure() override = default;
        bool configure() override { return _func(); }
    };

    template <class FunctionType>
    static std::unique_ptr<Configure>
    makeLambdaConfigure(FunctionType &&function)
    {
        return std::make_unique<LambdaConfigure<std::decay_t<FunctionType>>>
                (std::forward<FunctionType>(function));
    }

    /**
     * Interface used to signal when index manager has been reconfigured.
     */
    struct Reconfigurer {
        using Configure = searchcorespi::IIndexManager::Configure;
        virtual ~Reconfigurer();
        /**
         * Reconfigure index manager and infrastructure around it while system is in a quiescent state.
         */
        virtual bool reconfigure(std::unique_ptr<Configure> configure) = 0;
    };

    using UP = std::unique_ptr<IIndexManager>;
    using SP = std::shared_ptr<IIndexManager>;

    virtual ~IIndexManager() = default;

    /**
     * Inserts a document into the index. This method is async, caller
     * must either wait for notification about write done or sync
     * indexFieldWriter executor in threading service to get sync
     * behavior.
     *
     * If the inserted document id already exist the old version must
     * be removed before inserting the new.
     *
     * @param lid             The local document id for the document.
     *
     * @param doc             The document to insert.
     *
     * @param serialNum       The unique monotoninc increasing serial number
     *                        for this operation.
     *
     * @param on_write_done   shared object that notifies write done when
     *                        destructed.
     **/
    virtual void putDocument(uint32_t lid, const Document &doc, SerialNum serialNum, const OnWriteDoneType& on_write_done) = 0;

    /**
     * Removes the given document from the index. This method is
     * async, caller must either wait for notification about write
     * done or sync indexFieldWriter executor in threading service to
     * get sync behavior.
     *
     * @param lid             The local document id for the document.
     *
     * @param serialNum       The unique monotoninc increasing serial number
     *                        for this operation.
     **/
    void removeDocument(uint32_t lid, SerialNum serialNum) { 
        LidVector lids;
        lids.push_back(lid);
        removeDocuments(std::move(lids), serialNum);
    }
    virtual void removeDocuments(LidVector lids, SerialNum serialNum) = 0;

    /**
     * Commits the document puts and removes since the last commit,
     * making them searchable. This method is async, caller must
     * either wait for notification about write done or sync
     * indexFieldWriter executor in threading service to get sync
     * behavior.
     *
     * @param serialNum       The unique monotoninc increasing serial number
     *                        for this operation.
     *
     * @param onWriteDone     shared object that notifies write done when
     *                        destructed.
     **/
    virtual void commit(SerialNum serialNum, const OnWriteDoneType& onWriteDone) = 0;

    /**
     * This method is called on a regular basis to update each component with what is the highest
     * serial number for any component. This is for all components to be able to correctly tell its age.
     *
     * @param serialNum The serial number of the last known operation.
     */
    virtual void heartBeat(SerialNum serialNum) = 0;

    /**
     * This method is called when lid space is compacted.
     *
     * @param lidLimit  The new lid limit.
     * @param serialNum The serial number of the lid space compaction operation.
     */
    virtual void compactLidSpace(uint32_t lidLimit, SerialNum serialNum) = 0;

    /**
     * Returns the current serial number of the index.
     * This should also reflect any heart beats.
     *
     * @return current serial number of the component.
     **/
    virtual SerialNum getCurrentSerialNum() const = 0;

    /**
     * Returns the serial number of the last flushed index.
     *
     * @return the serial number of the last flushed index.
     **/
    virtual SerialNum getFlushedSerialNum() const = 0;

    /**
     * Returns the searchable that will give the correct search view of the index manager.
     * Normally switched everytime underlying index structures are changed in a way that can not be
     * handled in a thread safe way without locking. For instance flushing of memory index or
     * starting using a new schema.
     *
     * @return the current searchable.
     **/
    virtual IndexSearchable::SP getSearchable() const = 0;

    /**
     * Returns searchable stats for this index manager.
     *
     * @return statistics gathered about underlying memory and disk indexes.
     */
    virtual search::SearchableStats getSearchableStats(bool clear_disk_io_stats) const = 0;

    /**
     * Returns the list of all flush targets contained in this index manager.
     *
     * @return The list of flushable items in this component.
     **/
    virtual IFlushTarget::List getFlushTargets() = 0;

    /**
     * Sets the new schema to be used by this index manager.
     *
     * @param schema The new schema to start using.
     **/
    virtual void setSchema(const Schema &schema, SerialNum serialNum) = 0;

    /*
     * Sets the max number of flushed indexes before fusion is urgent.
     *
     * @param maxFlushed   The max number of flushed indexes before fusion is urgent.
     */
    virtual void setMaxFlushed(uint32_t maxFlushed) = 0;

    /**
     * Checks if we have a pending urgent flush due to a recent
     * schema change (e.g. regeneration of interleaved features in
     * disk indexes).
     *
     * @return whether an urgent flush is pending
     */
    virtual bool has_pending_urgent_flush() const = 0;
};

} // namespace searchcorespi

