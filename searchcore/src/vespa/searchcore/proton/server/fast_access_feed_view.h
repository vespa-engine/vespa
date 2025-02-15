// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storeonlyfeedview.h"
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/common/docid_limit.h>

namespace proton {

/**
 * The feed view used by the fast-access sub database.
 *
 * Handles inserting/updating/removing of documents to the underlying
 * fast-access attributes and document store.
 */
class FastAccessFeedView : public StoreOnlyFeedView
{
public:
    using SP = std::shared_ptr<FastAccessFeedView>;
    using UP = std::unique_ptr<FastAccessFeedView>;

    struct Context
    {
        const IAttributeWriter::SP   _attrWriter;
        DocIdLimit                  &_docIdLimit;
        Context(IAttributeWriter::SP attrWriter, DocIdLimit &docIdLimit)
            : _attrWriter(std::move(attrWriter)),
              _docIdLimit(docIdLimit)
        { }
    };

private:
    using Parent = StoreOnlyFeedView;

    const IAttributeWriter::SP _attributeWriter;
    DocIdLimit                 &_docIdLimit;

    void putAttributes(SerialNum serialNum, search::DocumentIdT lid, const Document &doc, const OnPutDoneType& onWriteDone) override;

    void updateAttributes(SerialNum serialNum, search::DocumentIdT lid, const document::DocumentUpdate &upd,
                          const OnOperationDoneType& onWriteDone, IFieldUpdateCallback & onUpdate) override;
    void updateAttributes(SerialNum serialNum, Lid lid, FutureDoc doc, const OnOperationDoneType& onWriteDone) override;
    void removeAttributes(SerialNum serialNum, search::DocumentIdT lid, const OnRemoveDoneType& onWriteDone) override;

    void removeAttributes(SerialNum serialNum, const LidVector &lidsToRemove, const OnWriteDoneType& onWriteDone) override;

    void heartBeatAttributes(SerialNum serialNum, const DoneCallback& onDone) override;

protected:
    void internalForceCommit(const CommitParam & param, const OnForceCommitDoneType& onCommitDone) override;

public:
    FastAccessFeedView(StoreOnlyFeedView::Context storeOnlyCtx, const PersistentParams &params, const Context &ctx);
    ~FastAccessFeedView() override;

    virtual const IAttributeWriter::SP &getAttributeWriter() const {
        return _attributeWriter;
    }

    virtual DocIdLimit &getDocIdLimit() const {
        return _docIdLimit;
    }

    void handleCompactLidSpace(const CompactLidSpaceOperation &op, const DoneCallback& onDone) override;
};

} // namespace proton

