// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentstoreadapter.h"
#include <vespa/searchsummary/docsummary/docsum_store_document.h>
#include <vespa/document/fieldvalue/document.h>
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
                     const DocumentTypeRepo &repo)
    : _docStore(docStore),
      _repo(repo)
{
}

DocumentStoreAdapter::~DocumentStoreAdapter() = default;

std::unique_ptr<const IDocsumStoreDocument>
DocumentStoreAdapter::get_document(uint32_t docId)
{
    auto document = _docStore.read(docId, _repo);
    if ( ! document) {
        LOG(debug, "Did not find summary document for docId %u. Returning empty docsum", docId);
        return {};
    }
    LOG(spam, "getMappedDocSum(%u): document={\n%s\n}", docId, document->toString(true).c_str());
    return std::make_unique<DocsumStoreDocument>(std::move(document));
}

} // namespace proton
