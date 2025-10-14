// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "docsum_store_field_value.h"
#include <vespa/searchcommon/common/element_ids.h>
#include <string>

namespace vespalib::slime { struct Inserter; }

namespace search::docsummary {

class IJuniperConverter;
class IStringFieldConverter;

/**
 * Interface class providing access to a document retrieved from an IDocsumStore.
 *
 * Some implementations (e.g. DocsumStoreVsmDocument) might apply transforms when accessing some fields.
 **/
class IDocsumStoreDocument
{
public:
    virtual ~IDocsumStoreDocument() = default;
    virtual DocsumStoreFieldValue get_field_value(const std::string& field_name) const = 0;
    void insert_summary_field(const std::string& field_name, search::common::ElementIds selected_documents,
                              vespalib::slime::Inserter& inserter) const {
        insert_summary_field(field_name, selected_documents, inserter, nullptr);
    }
    virtual void insert_summary_field(const std::string& field_name, search::common::ElementIds selected_elements,
                                      vespalib::slime::Inserter& inserter, IStringFieldConverter* converter) const = 0;
    virtual void insert_juniper_field(const std::string& field_name, search::common::ElementIds selected_elements,
                                      vespalib::slime::Inserter& inserter, IJuniperConverter& converter) const = 0;
    virtual void insert_document_id(vespalib::slime::Inserter& inserter) const = 0;
};

}
