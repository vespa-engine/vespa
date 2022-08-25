// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldcache.h"
#include <vespa/searchsummary/docsummary/docsumstore.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/docstore/idocumentstore.h>

namespace proton {

class DocumentStoreAdapter : public search::docsummary::IDocsumStore
{
private:
    const search::IDocumentStore           & _docStore;
    const document::DocumentTypeRepo       & _repo;
    const search::docsummary::ResultConfig & _resultConfig;
    const search::docsummary::ResultClass  * _resultClass;
    FieldCache::CSP                          _fieldCache;
    const std::set<vespalib::string>       & _markupFields;

public:
    DocumentStoreAdapter(const search::IDocumentStore &docStore,
                         const document::DocumentTypeRepo &repo,
                         const search::docsummary::ResultConfig &resultConfig,
                         const vespalib::string &resultClassName,
                         const FieldCache::CSP &fieldCache,
                         const std::set<vespalib::string> &markupFields);
    ~DocumentStoreAdapter();

    const search::docsummary::ResultClass *getResultClass() const {
        return _resultClass;
    }

    uint32_t getNumDocs() const override { return _docStore.getDocIdLimit(); }
    std::unique_ptr<const search::docsummary::IDocsumStoreDocument> getMappedDocsum(uint32_t docId) override;
    uint32_t getSummaryClassId() const override { return _resultClass->GetClassID(); }

};

} // namespace proton

