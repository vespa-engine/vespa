// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>

namespace search::expression { class AttributeNode; }

namespace search::expression::test {

std::unique_ptr<AttributeNode>
makeAttributeMapLookupNode(vespalib::stringref attributeName);

}
