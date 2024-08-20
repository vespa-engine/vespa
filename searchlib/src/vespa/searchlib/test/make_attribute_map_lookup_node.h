// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>

namespace search::expression { class AttributeNode; }

namespace search::expression::test {

std::unique_ptr<AttributeNode>
makeAttributeMapLookupNode(std::string_view attributeName);

}
