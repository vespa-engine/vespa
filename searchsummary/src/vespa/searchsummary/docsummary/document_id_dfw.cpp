// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_id_dfw.h"

#include "i_docsum_store_document.h"

#include <vespa/searchlib/common/i_document_id_provider.h>
#include <vespa/vespalib/data/slime/inserter.h>

using search::common::ElementIds;

namespace search::docsummary {

DocumentIdDFW::DocumentIdDFW(std::shared_ptr<const IDocumentIdProvider> document_id_provider)
    : DocsumFieldWriter(), _document_id_provider(std::move(document_id_provider)) {
}

DocumentIdDFW::~DocumentIdDFW() = default;

bool DocumentIdDFW::isGenerated() const {
    return _document_id_provider != nullptr;
}

void DocumentIdDFW::insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState&, ElementIds,
                                 vespalib::slime::Inserter& target) const {
    if (doc != nullptr) {
        if (doc->insert_document_id(target)) {
            return;
        }
    }
    if (_document_id_provider) {
        auto view = _document_id_provider->get_document_id_string_view(docid);
        if (!view.empty()) {
            vespalib::Memory memory_view(view.data(), view.size());
            target.insertString(memory_view);
        }
    }
}

} // namespace search::docsummary
