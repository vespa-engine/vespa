// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_docsum_store_document.h"

namespace document { class Document; }

namespace search::docsummary {

/**
 * Class providing access to a document retrieved from an IDocsumStore.
 **/
class DocsumStoreDocument : public IDocsumStoreDocument
{
    std::unique_ptr<document::Document> _document;
public:
    DocsumStoreDocument(std::unique_ptr<document::Document> document);
    ~DocsumStoreDocument() override;
    std::unique_ptr<document::FieldValue> get_field_value(const vespalib::string& field_name) const override;
    void insert_summary_field(const vespalib::string& field_name, vespalib::slime::Inserter& inserter) const override;
};

}
