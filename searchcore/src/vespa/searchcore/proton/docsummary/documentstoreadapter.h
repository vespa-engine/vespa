// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "fieldcache.h"
#include <vespa/searchsummary/docsummary/docsumstore.h>
#include <vespa/searchsummary/docsummary/resultconfig.h>
#include <vespa/searchsummary/docsummary/resultpacker.h>
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
    search::docsummary::ResultPacker         _resultPacker;
    FieldCache::CSP                          _fieldCache;
    const std::set<vespalib::string>       & _markupFields;

    bool
    writeStringField(const char * buf,
                     uint32_t buflen,
                     search::docsummary::ResType type);

    bool
    writeField(const document::FieldValue &value,
               search::docsummary::ResType type);

    void
    convertFromSearchDoc(document::Document &doc, uint32_t docId);

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
    search::docsummary::DocsumStoreValue getMappedDocsum(uint32_t docId) override;
    uint32_t getSummaryClassId() const override { return _resultClass->GetClassID(); }

};

} // namespace proton

