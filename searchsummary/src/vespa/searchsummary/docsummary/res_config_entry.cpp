// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "res_config_entry.h"
#include "docsum_field_writer.h"
#include "summary_elements_selector.h"

namespace search::docsummary {

ResConfigEntry::ResConfigEntry(const std::string& name_in) noexcept
    : _name(name_in),
      _elements_selector(),
      _writer(),
      _generated(false)
{
}

ResConfigEntry::~ResConfigEntry() = default;

ResConfigEntry::ResConfigEntry(ResConfigEntry&&) noexcept = default;

void
ResConfigEntry::set_elements_selector(const SummaryElementsSelector& elements_selector_in)
{
    _elements_selector = std::make_unique<SummaryElementsSelector>(elements_selector_in);
}

void
ResConfigEntry::set_writer(std::unique_ptr<DocsumFieldWriter> writer_in)
{
    _writer = std::move(writer_in);
    _generated = _writer ? _writer->isGenerated() : false;
}

}
