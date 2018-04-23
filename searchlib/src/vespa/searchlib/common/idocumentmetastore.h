// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_usage_stats.h"
#include <vespa/document/base/globalid.h>
#include <vespa/document/bucket/bucketid.h>
#include <persistence/spi/types.h>

namespace search {

/**
 * Meta data for a single document.
 **/
struct DocumentMetaData {
    typedef uint32_t DocId;
    DocId lid;
    storage::spi::Timestamp timestamp;
    document::BucketId bucketId;
    document::GlobalId gid;
    bool removed;

    typedef std::vector<DocumentMetaData> Vector;

    DocumentMetaData()
        : lid(0),
          timestamp(0),
          bucketId(),
          gid(),
          removed(false)
    { }

    DocumentMetaData(DocId lid_,
                     storage::spi::Timestamp timestamp_,
                     document::BucketId bucketId_,
                     const document::GlobalId &gid_)
        : lid(lid_),
          timestamp(timestamp_),
          bucketId(bucketId_),
          gid(gid_),
          removed(false)
    { }

    DocumentMetaData(DocId lid_,
                     storage::spi::Timestamp timestamp_,
                     document::BucketId bucketId_,
                     const document::GlobalId &gid_,
                     bool removed_)
        : lid(lid_),
          timestamp(timestamp_),
          bucketId(bucketId_),
          gid(gid_),
          removed(removed_)
    { }

    bool valid() const {
        return lid != 0 && timestamp != 0 && bucketId.isSet();
    }
};

namespace queryeval {

class Blueprint;

}

class IGidToLidMapperVisitor;


/**
 * Read interface for a document meta store that provides mapping between
 * global document id (gid) and local document id (lid) with additional
 * meta data per document.
 **/
struct IDocumentMetaStore {
    typedef uint32_t                DocId;
    typedef document::GlobalId      GlobalId;
    typedef document::BucketId      BucketId;
    typedef storage::spi::Timestamp Timestamp;

    virtual ~IDocumentMetaStore() {}

    /**
     * Retrieves the gid associated with the given lid.
     * Returns true if found, false otherwise.
     **/
    virtual bool getGid(DocId lid, GlobalId &gid) const = 0;
    /**
     * Retrieves the gid associated with the given lid, even if the lid has moved.
     * Returns true if found, false otherwise.
     **/
    virtual bool getGidEvenIfMoved(DocId lid, GlobalId &gid) const = 0;

    /**
     * Retrieves the lid associated with the given gid.
     * Returns true if found, false otherwise.
     **/
    virtual bool getLid(const GlobalId &gid, DocId &lid) const = 0;

    /**
     * Retrieves the meta data for the document with the given gid.
     **/
    virtual DocumentMetaData getMetaData(const GlobalId &gid) const = 0;

    /**
     * Retrieves meta data for all documents contained in the given bucket.
     **/
    virtual void getMetaData(const BucketId &bucketId, DocumentMetaData::Vector &result) const = 0;

    /**
     * Returns the lid following the largest lid used in the store.
     *
     * As long as the reader holds a read guard on the document meta
     * store, we guarantee that the meta store info for lids that were
     * valid when calling this method will remain valid while the
     * guard is held, i.e. lids for newly removed documents are not
     * reused while the read guard is held.
     *
     * Access to lids beyond the returned limit is not safe.
     *
     * The return value can be used as lid range for queries when
     * attribute writer threads are synced, and is propagated as such
     * when visibility delay is nonzero and forceCommit() method is
     * called regularly on feed views, cf. proton::FastAccessFeedView.
     *
     * In the future, this method might be renamed to getReaderDocIdLimit().
     **/
    virtual DocId getCommittedDocIdLimit() const = 0;

    /**
     * Returns the number of used lids in this store.
     */
    virtual DocId getNumUsedLids() const = 0;

    /**
     * Returns the number of active lids in this store.
     * This should be <= getNumUsedLids().
     * Active lids correspond to documents in active buckets.
     */
    virtual DocId getNumActiveLids() const = 0;

    /**
     * Returns stats on the usage and availability of lids in this store.
     */
    virtual LidUsageStats getLidUsageStats() const = 0;

    /**
     * Creates a white list blueprint that returns a search iterator
     * that gives hits for all documents that should be visible.
     **/
    virtual std::unique_ptr<queryeval::Blueprint> createWhiteListBlueprint() const = 0;

    /**
     * Give read access to the current generation of the metastore.
     **/
    virtual uint64_t getCurrentGeneration() const = 0;

    virtual void foreach(const IGidToLidMapperVisitor &visitor) const = 0;
};


}

