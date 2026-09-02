// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "res_config_entry.h"

#include "docsum_field_writer.h"
#include "slime_filler_filter.h"
#include "summary_elements_selector.h"

namespace search::docsummary {

ResConfigEntry::ResConfigEntry(const std::string& name_in) noexcept
    : _name(name_in), _elements_selector(), _writer(), _struct_fields_filter(), _generated(false) {
}

ResConfigEntry::~ResConfigEntry() = default;

ResConfigEntry::ResConfigEntry(ResConfigEntry&&) noexcept = default;

void ResConfigEntry::set_elements_selector(const SummaryElementsSelector& elements_selector_in) {
    _elements_selector = std::make_unique<SummaryElementsSelector>(elements_selector_in);
}

void ResConfigEntry::set_writer(std::unique_ptr<DocsumFieldWriter> writer_in) {
    _writer = std::move(writer_in);
    _generated = _writer ? _writer->isGenerated() : false;
}

void ResConfigEntry::set_struct_fields(std::span<const std::string> struct_fields_in) {
    if (struct_fields_in.empty()) {
        _struct_fields_filter.reset();
        return;
    }
    // The names are used as given: one which is no sub-field of the field just matches nothing here,
    // rejecting it belongs to the config model.
    auto filter = std::make_unique<SlimeFillerFilter>();
    for (const auto& struct_field : struct_fields_in) {
        filter->add(struct_field);
    }
    _struct_fields_filter = std::move(filter);
}

} // namespace search::docsummary
