// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_store_document.h"
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>

namespace search::docsummary {

DocsumStoreDocument::DocsumStoreDocument(std::unique_ptr<document::Document> document)
    : _document(std::move(document))
{
}

DocsumStoreDocument::~DocsumStoreDocument() = default;

std::unique_ptr<document::FieldValue>
DocsumStoreDocument::get_field_value(const vespalib::string& field_name) const
{
    if (_document) {
        const document::Field& field = _document->getField(field_name);
        auto value(field.getDataType().createFieldValue());
        if (value) {
            if (_document->getValue(field, *value)) {
                return value;
            }
        }
    }
    return {};
}

}
