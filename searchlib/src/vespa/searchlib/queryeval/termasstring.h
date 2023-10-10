// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::query { class Node; }

namespace search::queryeval {

vespalib::string termAsString(const search::query::Node &term_node);
vespalib::stringref termAsString(const search::query::Node &term_node, vespalib::string & scratchPad);

}
