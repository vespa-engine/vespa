// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace search::query { class Node; }

namespace search::queryeval {

std::string termAsString(const search::query::Node &term_node);
std::string_view termAsString(const search::query::Node &term_node, std::string & scratchPad);

}
