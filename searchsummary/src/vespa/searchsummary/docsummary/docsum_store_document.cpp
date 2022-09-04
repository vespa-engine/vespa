// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "docsum_store_document.h"
#include "check_undefined_value_visitor.h"
#include "summaryfieldconverter.h"
#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/data/slime/inserter.h>

namespace search::docsummary {

DocsumStoreDocument::DocsumStoreDocument(std::unique_ptr<document::Document> document)
    : _document(std::move(document))
{
}

DocsumStoreDocument::~DocsumStoreDocument() = default;

DocsumStoreFieldValue
DocsumStoreDocument::get_field_value(const vespalib::string& field_name) const
{
    if (_document) {
        const document::Field& field = _document->getField(field_name);
        auto value(field.getDataType().createFieldValue());
        if (value) {
            try {
                if (_document->getValue(field, *value)) {
                    return DocsumStoreFieldValue(std::move(value));
                }
            } catch (document::FieldNotFoundException&) {
                // Field was not found in document type. Return empty value.
            }
        }
    }
    return DocsumStoreFieldValue();
}

JuniperInput
DocsumStoreDocument::get_juniper_input(const vespalib::string& field_name) const
{
    auto field_value = get_field_value(field_name);
    if (field_value) {
        auto field_value_with_markup = SummaryFieldConverter::convertSummaryField(true, *field_value);
        return JuniperInput(DocsumStoreFieldValue(std::move(field_value_with_markup)));
    }
    return {};
}

void
DocsumStoreDocument::insert_summary_field(const vespalib::string& field_name, vespalib::slime::Inserter& inserter) const
{
    try {
        auto field_value = get_field_value(field_name);
        if (field_value) {
            SummaryFieldConverter::insert_summary_field(*field_value, inserter);
        }
    } catch (document::FieldNotFoundException&) {
        // Field was not found in document type. Don't insert anything.
    }
}

void
DocsumStoreDocument::insert_document_id(vespalib::slime::Inserter& inserter) const
{
    if (_document) {
        auto id = _document->getId().toString();
        vespalib::Memory id_view(id.data(), id.size());
        inserter.insertString(id_view);
    }
}

}
