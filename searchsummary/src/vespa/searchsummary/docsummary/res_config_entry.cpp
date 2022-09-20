// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "res_config_entry.h"
#include "docsum_field_writer.h"

namespace search::docsummary {

ResConfigEntry::ResConfigEntry() noexcept
    : _name(),
      _docsum_field_writer()
{
}

ResConfigEntry::~ResConfigEntry() = default;

ResConfigEntry::ResConfigEntry(ResConfigEntry&&) noexcept = default;

}
