// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "copy_dfw.h"

#include "i_docsum_store_document.h"
#include "slime_filler_filter.h"

#include <vespa/vespalib/data/slime/slime.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.docsummary.copy_dfw");

using search::common::ElementIds;

namespace search::docsummary {

CopyDFW::CopyDFW(const std::string& inputField, std::span<const std::string> struct_fields)
    : _input_field_name(inputField), _struct_fields_filter() {
    if (!struct_fields.empty()) {
        auto filter = std::make_unique<SlimeFillerFilter>();
        for (const auto& struct_field : struct_fields) {
            filter->add(struct_field);
        }
        _struct_fields_filter = std::move(filter);
    }
}

CopyDFW::~CopyDFW() = default;

void CopyDFW::insert_field(uint32_t, const IDocsumStoreDocument* doc, GetDocsumsState&, ElementIds selected_elements,
                           vespalib::slime::Inserter& target) const {
    if (doc != nullptr) {
        doc->insert_summary_field(_input_field_name, selected_elements, target, nullptr, _struct_fields_filter.get());
    }
}

} // namespace search::docsummary
