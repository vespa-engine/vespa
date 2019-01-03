// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "indexsearchable.h"
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcorespi/flush/flushstats.h>
#include <vespa/searchcorespi/flush/iflushtarget.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/util/closure.h>

namespace search { class IDestructorCallback; }
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
    typedef document::Document Document;
    typedef search::SerialNum SerialNum;
    typedef search::index::Schema Schema;

public:
    using OnWriteDoneType = const std::shared_ptr<search::IDestructorCallback> &;
    /**
     * Interface used to signal when index manager has been reconfigured.
     */
    struct Reconfigurer {
        virtual ~Reconfigurer();
        /**
         * Reconfigure index manager and infrastructure around it while system is in a quiescent state.
         */
        virtual bool reconfigure(vespalib::Closure0<bool>::UP closure) = 0;
    };

    typedef std::unique_ptr<IIndexManager> UP;
    typedef std::shared_ptr<IIndexManager> SP;

    virtual ~IIndexManager() {}

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
     **/
    virtual void putDocument(uint32_t lid, const Document &doc, SerialNum serialNum) = 0;

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
    virtual void removeDocument(uint32_t lid, SerialNum serialNum) = 0;

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
    virtual void commit(SerialNum serialNum, OnWriteDoneType onWriteDone) = 0;

    /**
     * This method is called on a regular basis to update each component with what is the highest
     * serial number for any component. This is for all components to be able to correctly tell its age.
     *
     * @param serialNum The serial number of the last known operation.
     */
    virtual void heartBeat(SerialNum serialNum) = 0;

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
    virtual search::SearchableStats getSearchableStats() const = 0;

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
};

} // namespace searchcorespi

