// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "res_type.h"
#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::docsummary {

class DocsumFieldWriter;

/**
 * This struct describes a single docsum field (name and type).
 **/
struct ResConfigEntry {
    ResType          _type;
    vespalib::string _bindname;
    int              _enumValue;
    std::unique_ptr<DocsumFieldWriter> _docsum_field_writer;
    ResConfigEntry() noexcept;
    ~ResConfigEntry();
    ResConfigEntry(ResConfigEntry&&) noexcept;
};

}
