// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_store_field_value.h"

#include <vespa/searchcommon/common/element_ids.h>

#include <string>

namespace vespalib::slime {
struct Inserter;
}

namespace search::docsummary {

class IJuniperConverter;
class IStringFieldConverter;
class SlimeFillerFilter;

/**
 * Interface class providing access to a document retrieved from an IDocsumStore.
 *
 * Some implementations (e.g. DocsumStoreVsmDocument) might apply transforms when accessing some fields.
 **/
class IDocsumStoreDocument {
public:
    virtual ~IDocsumStoreDocument() = default;
    virtual DocsumStoreFieldValue get_field_value(const std::string& field_name) const = 0;
    void insert_summary_field(const std::string& field_name, search::common::ElementIds selected_documents,
                              vespalib::slime::Inserter& inserter) const {
        insert_summary_field(field_name, selected_documents, inserter, nullptr, nullptr);
    }
    void insert_summary_field(const std::string& field_name, search::common::ElementIds selected_documents,
                              vespalib::slime::Inserter& inserter, IStringFieldConverter* converter) const {
        insert_summary_field(field_name, selected_documents, inserter, converter, nullptr);
    }
    /**
     * Inserts the field, keeping only the struct sub-fields matched by struct_fields_filter when that is
     * not nullptr. The filter belongs to the field in the requested document-summary, cf.
     * ResConfigEntry::struct_fields_filter(), so it is passed in here rather than known by the document:
     * the same document is used for every document-summary.
     */
    virtual void insert_summary_field(const std::string& field_name, search::common::ElementIds selected_elements,
                                      vespalib::slime::Inserter& inserter, IStringFieldConverter* converter,
                                      const SlimeFillerFilter* struct_fields_filter) const = 0;
    virtual void insert_juniper_field(const std::string& field_name, search::common::ElementIds selected_elements,
                                      vespalib::slime::Inserter& inserter, IJuniperConverter& converter) const = 0;
    [[nodiscard]] virtual bool insert_document_id(vespalib::slime::Inserter& inserter) const = 0;
};

} // namespace search::docsummary
