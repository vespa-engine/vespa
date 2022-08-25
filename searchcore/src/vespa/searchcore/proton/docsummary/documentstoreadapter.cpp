// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentstoreadapter.h"
#include <vespa/searchsummary/docsummary/docsum_store_document.h>
#include <vespa/searchsummary/docsummary/summaryfieldconverter.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/document/fieldvalue/tensorfieldvalue.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.docsummary.documentstoreadapter");

using namespace document;
using namespace search::docsummary;

namespace proton {

namespace {

const vespalib::string DOCUMENT_ID_FIELD("documentid");

}

DocumentStoreAdapter::
DocumentStoreAdapter(const search::IDocumentStore & docStore,
                     const DocumentTypeRepo &repo,
                     const ResultConfig & resultConfig,
                     const vespalib::string & resultClassName,
                     const FieldCache::CSP & fieldCache,
                     const std::set<vespalib::string> &markupFields)
    : _docStore(docStore),
      _repo(repo),
      _resultConfig(resultConfig),
      _resultClass(resultConfig.
                   LookupResultClass(resultConfig.LookupResultClassId(resultClassName.c_str()))),
      _fieldCache(fieldCache),
      _markupFields(markupFields)
{
}

DocumentStoreAdapter::~DocumentStoreAdapter() = default;

std::unique_ptr<const IDocsumStoreDocument>
DocumentStoreAdapter::getMappedDocsum(uint32_t docId)
{
    Document::UP document = _docStore.read(docId, _repo);
    if ( ! document) {
        LOG(debug, "Did not find summary document for docId %u. Returning empty docsum", docId);
        return {};
    }
    LOG(spam, "getMappedDocSum(%u): document={\n%s\n}", docId, document->toString(true).c_str());
    return std::make_unique<DocsumStoreDocument>(std::move(document));
}

} // namespace proton
