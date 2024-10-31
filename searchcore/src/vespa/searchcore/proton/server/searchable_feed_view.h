// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    using Parent = FastAccessFeedView;
public:
    using UP = std::unique_ptr<SearchableFeedView>;
    using SP = std::shared_ptr<SearchableFeedView>;

    struct Context {
        const IIndexWriter::SP &_indexWriter;

        Context(const IIndexWriter::SP &indexWriter);
        ~Context();
    };

private:
    const IIndexWriter::SP    _indexWriter;
    const bool                _hasIndexedFields;

    bool hasIndexedFields() const { return _hasIndexedFields; }

    void performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const Document &doc, const OnOperationDoneType& onWriteDone);
    void performIndexPut(SerialNum serialNum, search::DocumentIdT lid, const DocumentSP &doc, const OnOperationDoneType& onWriteDone);
    void performIndexPut(SerialNum serialNum, search::DocumentIdT lid, FutureDoc doc, const OnOperationDoneType& onWriteDone);
    void performIndexRemove(SerialNum serialNum, search::DocumentIdT lid, const OnRemoveDoneType& onWriteDone);
    void performIndexRemove(SerialNum serialNum, const LidVector &lidsToRemove, const OnWriteDoneType& onWriteDone);
    void performIndexHeartBeat(SerialNum serialNum);

    void internalDeleteBucket(const DeleteBucketOperation &delOp, const DoneCallback& onDone) override;
    void heartBeatIndexedFields(SerialNum serialNum, const DoneCallback& onDone) override;

    void putIndexedFields(SerialNum serialNum, search::DocumentIdT lid, const DocumentSP &newDoc, const OnOperationDoneType& onWriteDone) override;
    void updateIndexedFields(SerialNum serialNum, search::DocumentIdT lid, FutureDoc newDoc, const OnOperationDoneType& onWriteDone) override;
    void removeIndexedFields(SerialNum serialNum, search::DocumentIdT lid, const OnRemoveDoneType& onWriteDone) override;
    void removeIndexedFields(SerialNum serialNum, const LidVector &lidsToRemove, const OnWriteDoneType& onWriteDone) override;

    void performIndexForceCommit(SerialNum serialNum, const OnForceCommitDoneType& onCommitDone);
    void internalForceCommit(const CommitParam & param, const OnForceCommitDoneType& onCommitDone) override;

public:
    SearchableFeedView(StoreOnlyFeedView::Context storeOnlyCtx, const PersistentParams &params,
                       const FastAccessFeedView::Context &fastUpdateCtx, Context ctx);

    ~SearchableFeedView() override;
    const IIndexWriter::SP &getIndexWriter() const { return _indexWriter; }
    void handleCompactLidSpace(const CompactLidSpaceOperation &op, const DoneCallback& onDone) override;
};

} // namespace proton
