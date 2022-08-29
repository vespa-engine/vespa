// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "res_config_entry.h"

namespace search::docsummary {

ResConfigEntry::ResConfigEntry() noexcept
    : _type(RES_BAD),
      _bindname(),
      _enumValue(0)
{
}

ResConfigEntry::~ResConfigEntry() = default;

}
