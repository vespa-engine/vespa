// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "storeonlyfeedview.h"
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/document/fieldvalue/document.h>

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
    typedef std::shared_ptr<FastAccessFeedView> SP;
    typedef std::unique_ptr<FastAccessFeedView> UP;

    struct Context
    {
        const IAttributeWriter::SP &_attrWriter;
        DocIdLimit                  &_docIdLimit;
        Context(const IAttributeWriter::SP &attrWriter,
                DocIdLimit &docIdLimit)
            : _attrWriter(attrWriter),
              _docIdLimit(docIdLimit)
        { }
    };

private:
    typedef StoreOnlyFeedView Parent;

    const IAttributeWriter::SP _attributeWriter;
    DocIdLimit                 &_docIdLimit;

    UpdateScope getUpdateScope(const document::DocumentUpdate &upd) override;

    void putAttributes(SerialNum serialNum, search::DocumentIdT lid, const document::Document &doc,
                       bool immediateCommit, OnPutDoneType onWriteDone) override;

    void updateAttributes(SerialNum serialNum, search::DocumentIdT lid, const document::DocumentUpdate &upd,
                          bool immediateCommit, OnOperationDoneType onWriteDone) override;
    void updateAttributes(SerialNum serialNum, Lid lid, FutureDoc doc,
                          bool immediateCommit, OnOperationDoneType onWriteDone) override;
    void removeAttributes(SerialNum serialNum, search::DocumentIdT lid,
                          bool immediateCommit, OnRemoveDoneType onWriteDone) override;

    void removeAttributes(SerialNum serialNum, const LidVector &lidsToRemove,
                          bool immediateCommit, OnWriteDoneType onWriteDone) override;

    void heartBeatAttributes(SerialNum serialNum) override;

protected:
    void forceCommit(SerialNum serialNum, OnForceCommitDoneType onCommitDone) override;

public:
    FastAccessFeedView(const StoreOnlyFeedView::Context &storeOnlyCtx,
                       const PersistentParams &params, const Context &ctx);
    ~FastAccessFeedView();

    virtual const IAttributeWriter::SP &getAttributeWriter() const {
        return _attributeWriter;
    }

    virtual DocIdLimit &getDocIdLimit() const {
        return _docIdLimit;
    }

    void handleCompactLidSpace(const CompactLidSpaceOperation &op) override;
    void sync() override;

    bool fastPartialUpdateAttribute(const vespalib::string &fieldName) const;
};

} // namespace proton

