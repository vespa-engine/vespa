// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "lid_gid_key_comparator.h"
#include "i_simple_document_meta_store.h"
#include <vespa/searchlib/attribute/attributeguard.h>
#include <vespa/searchlib/btree/btree.h>
#include <vespa/searchlib/btree/btreenodeallocator.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/searchlib/common/serialnum.h>

namespace proton {

/**
 * Interface used to manage the documents that are contained
 * in a document sub database with related meta data.
 *
 * A document meta store will have storage of <lid, meta data> pairs
 * (local document id, meta data (including global document id)) and
 * mapping from lid -> meta data and gid -> lid.
 **/
struct IDocumentMetaStore : public search::IDocumentMetaStore,
                            public ISimpleDocumentMetaStore
{
    using search::IDocumentMetaStore::DocId;
    using search::IDocumentMetaStore::GlobalId;
    using search::IDocumentMetaStore::BucketId;
    using search::IDocumentMetaStore::Timestamp;

    // Typedef for the tree used to map from gid -> lid
    // Lids are stored as keys in the tree, sorted by their gid counterpart.
    // The LidGidKeyComparator class maps from lids -> metadata by using the metadata store.
    // TODO(geirst): move this typedef and iterator functions away from this interface.
    typedef search::btree::BTree<DocId,
            search::btree::BTreeNoLeafData,
            search::btree::NoAggregated,
            const documentmetastore::LidGidKeyComparator &> TreeType;
    typedef TreeType::Iterator Iterator;
    typedef std::shared_ptr<IDocumentMetaStore> SP;

    virtual ~IDocumentMetaStore() {}

    /**
     * Constructs a new underlying free list for lids.
     * This should be done after a load() and calls to put() and remove().
     **/
    virtual void constructFreeList() = 0;

    virtual Iterator begin() const = 0;

    virtual Iterator lowerBound(const BucketId &bucketId) const = 0;

    virtual Iterator upperBound(const BucketId &bucketId) const = 0;

    virtual Iterator lowerBound(const GlobalId &gid) const = 0;

    virtual Iterator upperBound(const GlobalId &gid) const = 0;

    virtual void getLids(const BucketId &bucketId, std::vector<DocId> &lids) = 0;

    /*
     * Called by document db executor to hold unblocking of shrinking of lid
     * space after all outstanding holdLid() operations at the time of
     * compactLidSpace() call have been completed.
     */
    virtual void holdUnblockShrinkLidSpace() = 0;

    // Functions that are also defined search::AttributeVector
    virtual void commit(search::SerialNum firstSerialNum,
                        search::SerialNum lastSerialNum) = 0;
    virtual void removeAllOldGenerations() = 0;
    virtual bool canShrinkLidSpace() const = 0;
    virtual search::SerialNum getLastSerialNum() const = 0;

    /*
     * Adjust committedDocIdLimit downwards and prepare for shrinking
     * of lid space.
     *
     * NOTE: Must call unblockShrinkLidSpace() before lid space can
     * be shrunk.
     */
    virtual void compactLidSpace(DocId wantedLidLimit) = 0;

};

} // namespace proton

