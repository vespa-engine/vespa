// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_field_writer.h"
#include <memory>

namespace search { class IDocumentIdProvider; }

namespace search::docsummary {

/*
 * Class for writing document id field.
 */
class DocumentIdDFW : public DocsumFieldWriter
{
private:
    std::shared_ptr<const IDocumentIdProvider> _document_id_provider;
public:
    DocumentIdDFW(std::shared_ptr<const IDocumentIdProvider> document_id_provider);
    ~DocumentIdDFW() override;
    bool isGenerated() const override;
    void insert_field(uint32_t docid, const IDocsumStoreDocument* doc, GetDocsumsState& state,
                      search::common::ElementIds selected_elements,
                      vespalib::slime::Inserter &target) const override;
};

}
