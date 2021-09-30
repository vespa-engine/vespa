// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/searchcorespi/index/indexsearchable.h>
#include <vespa/searchlib/common/serialnum.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/memoryusage.h>

namespace vespalib { class IDestructorCallback; }

namespace searchcorespi::index {

/**
 * Interface for a memory index as seen from an index maintainer.
 */
struct IMemoryIndex : public searchcorespi::IndexSearchable {
    using SP = std::shared_ptr<IMemoryIndex>;
    using OnWriteDoneType = const std::shared_ptr<vespalib::IDestructorCallback> &;
    virtual ~IMemoryIndex() {}

    /**
     * Returns true if this memory index has received any document insert operations.
     */
    virtual bool hasReceivedDocumentInsert() const = 0;

    /**
     * Returns the memory usage of this memory index.
     */
    virtual vespalib::MemoryUsage getMemoryUsage() const = 0;

    /**
     * Returns the memory usage of an empty version of this memory index.
     */
    virtual uint64_t getStaticMemoryFootprint() const = 0;

    /**
     * Inserts the given document into this memory index.
     * If the document already exists it should be removed first.
     *
     * @param lid the local document id.
     * @param doc the document to insert.
     */
    virtual void insertDocument(uint32_t lid, const document::Document &doc) = 0;

    /**
     * Removes the given document from this memory index.
     *
     * @param lid the local document id.
     */
    virtual void removeDocument(uint32_t lid) = 0;

    /**
     * Commits the inserts and removes since the last commit, making them searchable.
     **/
    virtual void commit(OnWriteDoneType onWriteDone, search::SerialNum serialNum) = 0;

    /**
     * Flushes this memory index to disk as a disk index.
     * After a flush it should be possible to load a IDiskIndex from the flush directory.
     * Note that the schema used when constructing the memory index should be flushed as well
     * since a IDiskIndex should be able to return the schema used by the disk index.
     *
     * @param flushDir the directory in which to save the flushed index.
     * @param docIdLimit the largest local document id used + 1
     * @param serialNum the serial number of the last operation to the memory index.
     */
    virtual void flushToDisk(const vespalib::string &flushDir,
                             uint32_t docIdLimit,
                             search::SerialNum serialNum) = 0;

    virtual void pruneRemovedFields(const search::index::Schema &schema) = 0;
    virtual search::index::Schema::SP getPrunedSchema() const = 0;
};

}


