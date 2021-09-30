// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fast_access_feed_view.h"
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>

namespace proton {

/**
 * The feed view used by the searchable sub database.
 *
 * Handles inserting/updating/removing of documents to the underlying attributes,
 * memory index and document store.
 */
class SearchableFeedView : public FastAccessFeedView
{
    typedef FastAccessFeedView Parent;
public:
    typedef std::unique_ptr<SearchableFeedView> UP;
    typedef std::shared_ptr<SearchableFeedView> SP;

    struct Context {
        const IIndexWriter::SP &_indexWriter;

        Context(const IIndexWriter::SP &indexWriter);
        ~Context();
    };

private:
    const IIndexWriter::SP    _indexWriter;
    const bool                _hasIndexedFields;

    bool hasIndexedFields() const { return _hasIndexedFields; }

    void performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const Document &doc, OnOperationDoneType onWriteDone);
    void performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const DocumentSP &doc, OnOperationDoneType onWriteDone);
    void performIndexPut(SerialNum serialNum, search::DocumentIdT lid, FutureDoc doc, OnOperationDoneType onWriteDone);
    void performIndexRemove(SerialNum serialNum, search::DocumentIdT lid, OnRemoveDoneType onWriteDone);
    void performIndexRemove(SerialNum serialNum, const LidVector &lidsToRemove, OnWriteDoneType onWriteDone);
    void performIndexHeartBeat(SerialNum serialNum);

    void internalDeleteBucket(const DeleteBucketOperation &delOp) override;
    void performSync();
    void heartBeatIndexedFields(SerialNum serialNum) override;

    void putIndexedFields(SerialNum serialNum, search::DocumentIdT lid, const DocumentSP &newDoc, OnOperationDoneType onWriteDone) override;
    void updateIndexedFields(SerialNum serialNum, search::DocumentIdT lid, FutureDoc newDoc, OnOperationDoneType onWriteDone) override;
    void removeIndexedFields(SerialNum serialNum, search::DocumentIdT lid, OnRemoveDoneType onWriteDone) override;
    void removeIndexedFields(SerialNum serialNum, const LidVector &lidsToRemove, OnWriteDoneType onWriteDone) override;

    void performIndexForceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone);
    void internalForceCommit(const CommitParam & param, OnForceCommitDoneType onCommitDone) override;

public:
    SearchableFeedView(StoreOnlyFeedView::Context storeOnlyCtx, const PersistentParams &params,
                       const FastAccessFeedView::Context &fastUpdateCtx, Context ctx);

    ~SearchableFeedView() override;
    const IIndexWriter::SP &getIndexWriter() const { return _indexWriter; }
    void handleCompactLidSpace(const CompactLidSpaceOperation &op) override;
    void sync() override;
};

} // namespace proton
