// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <string>

namespace document::select { class Node; }

namespace storage::distributor {

/**
 * Interface to parse a document selection string.
 */
class DocumentSelectionParser {
public:
    virtual ~DocumentSelectionParser() = default;
    virtual std::unique_ptr<document::select::Node> parse_selection(const std::string& str) const = 0;
};

}
