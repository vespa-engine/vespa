// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_store_document.h"
#include "check_undefined_value_visitor.h"
#include "summaryfieldconverter.h"
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

void
DocsumStoreDocument::insert_summary_field(const vespalib::string& field_name, vespalib::slime::Inserter& inserter) const
{
    auto field_value = get_field_value(field_name);
    if (field_value) {
        CheckUndefinedValueVisitor check_undefined;
        field_value->accept(check_undefined);
        if (!check_undefined.is_undefined()) {
            SummaryFieldConverter::insert_summary_field(false, *field_value, inserter);
        }
    }
}

}
