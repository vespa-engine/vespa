// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/common/serialnum.h>
#include <vespa/searchlib/docstore/idocumentstore.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/feedoperation/lidvectorcontext.h>

namespace search { class BitVector; }
namespace proton {

class DocStoreValidator : public search::IDocumentStoreReadVisitor
{
    IDocumentMetaStore                 &_dms;
    uint32_t                            _docIdLimit;
    std::unique_ptr<search::BitVector>  _invalid;
    std::unique_ptr<search::BitVector>  _orphans;
    uint32_t                            _visitCount;
    uint32_t                            _visitEmptyCount;
    
public:
    DocStoreValidator(IDocumentMetaStore &dms);

    virtual void visit(uint32_t lid, const std::shared_ptr<document::Document> &doc) override;
    virtual void visit(uint32_t lid) override;

    void visitDone();
    void killOrphans(search::IDocumentStore &store, search::SerialNum serialNum);
    uint32_t getInvalidCount() const;
    uint32_t getOrphanCount() const;
    uint32_t getVisitCount() const { return _visitCount; }
    uint32_t getVisitEmptyCount() const { return _visitEmptyCount; }
    LidVectorContext::SP getInvalidLids() const;
};


} // namespace proton

