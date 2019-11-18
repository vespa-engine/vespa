// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "raw_document_meta_data.h"
#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucketid.h>
#include <persistence/spi/types.h>

namespace proton::documentmetastore {


/**
 * Interface for storing information about mapping between global document id (gid)
 * and local document id (lid) with additional meta data per document.
 **/
struct IStore
{
    typedef uint32_t                DocId;
    typedef document::GlobalId      GlobalId;
    typedef document::BucketId      BucketId;
    typedef storage::spi::Timestamp Timestamp;

    /**
     * Result after lookup in the store.
     */
    struct Result
    {
        DocId     _lid;
        bool      _success;
        // Info about previous state.
        bool      _found; // gid was known (due to earlier put or remove)
        Timestamp _timestamp; // previous timestamp

        Result()
            : _lid(0u),
              _success(false),
              _found(false),
              _timestamp()
        {
        }
        void setLid(DocId lid) { _lid = lid; }
        DocId getLid() const { return _lid; }
        bool ok() const { return _success; }
        void markSuccess() { _success = true; }
        void fillPrev(Timestamp prevTimestamp) {
            _found = true;
            _timestamp = prevTimestamp;
        }
    };

    virtual ~IStore() {}

    /**
     * Inspect the meta data associated with the given gid.
     * If the gid is not found the result is not valid.
     */
    virtual Result inspectExisting(const GlobalId &gid) const = 0;

    /**
     * Inspect the meta data associated with the given gid.
     * If the gid is not found the next available lid is returned in the result.
     * This lid can be used if calling put() right afterwards.
     */
    virtual Result inspect(const GlobalId &gid) = 0;

    /**
     * Puts the given <lid, meta data> pair to this store.
     * This function should assert that the given lid is the same
     * as returned from inspect().
     **/
    virtual Result put(const GlobalId &gid,
                       const BucketId &bucketId,
                       const Timestamp &timestamp,
                       uint32_t docSize,
                       DocId lid) = 0;

    /*
     * Update the meta data associated with the given lid.
     * Used when handling partial updates.
     * Returns false if there is no entry for the given lid.
     */
    virtual bool updateMetaData(DocId lid,
                                const BucketId &bucketId,
                                const Timestamp &timestamp) = 0;

    /**
     * Removes the <lid, meta data> pair with the given lid from this
     * store. Returns false if the <lid, meta data> pair was not
     * found or could not be removed.
     * The caller must call removeComplete() after document removal is done.
     **/
    virtual bool remove(DocId lid) = 0;

    /**
     * Signal that the removal of the document associated with this lid is complete.
     * This is typically called after the document has been removed from all
     * other data structures. The lid is now a candidate for later reuse.
     */
    virtual void removeComplete(DocId lid) = 0;

    /**
     * Move meta data for fromLid to toLid. Mapping from gid to lid
     * is updated atomically from fromLid to toLid.
     * The caller must call removeComplete() with fromLid after document move is done.
     */
    virtual void move(DocId fromLid, DocId toLid) = 0;

    /**
     * Check if the lid is valid.
     * Returns true if valid, false otherwise.
     **/
    virtual bool validLid(DocId lid) const = 0;

    /**
     * Removes a list of lids.
     * The caller must call removeBatchComplete() after documents removal is done.
     */
    virtual void removeBatch(const std::vector<DocId> &lidsToRemove, const DocId docIdLimit) = 0;

    /**
     * Signal that the removal of the documents associated with these lids is complete.
     */
    virtual void removeBatchComplete(const std::vector<DocId> &lidsToRemove) = 0;

    /**
     * Returns the raw meta data stored for the given lid.
     */
    virtual const RawDocumentMetaData &getRawMetaData(DocId lid) const = 0;

    /**
     * Check if free list is active.
     *
     * Returns true if free list is active.
     */
    virtual bool getFreeListActive() const = 0;
};

}

