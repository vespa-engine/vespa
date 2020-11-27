// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace document::select { class Node; }

namespace storage::distributor {

/**
 * Interface to parse a document selection string.
 */
class DocumentSelectionParser {
public:
    virtual ~DocumentSelectionParser() {}
    virtual std::unique_ptr<document::select::Node> parse_selection(const vespalib::string& str) const = 0;
};

}
