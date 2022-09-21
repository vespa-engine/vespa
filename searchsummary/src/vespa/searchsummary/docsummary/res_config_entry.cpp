// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "res_config_entry.h"
#include "docsum_field_writer.h"

namespace search::docsummary {

ResConfigEntry::ResConfigEntry(const vespalib::string& name_in) noexcept
    : _name(name_in),
      _writer(),
      _generated(false)
{
}

ResConfigEntry::~ResConfigEntry() = default;

ResConfigEntry::ResConfigEntry(ResConfigEntry&&) noexcept = default;

void
ResConfigEntry::set_writer(std::unique_ptr<DocsumFieldWriter> writer_in)
{
    _writer = std::move(writer_in);
    _generated = _writer ? _writer->isGenerated() : false;
}

}
