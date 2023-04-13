// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "make_attribute_map_lookup_node.h"
#include <vespa/searchlib/expression/attribute_map_lookup_node.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace search::expression::test {

namespace {

vespalib::string indirectKeyMarker("attribute(");

}

std::unique_ptr<AttributeNode>
makeAttributeMapLookupNode(vespalib::stringref attributeName)
{
    vespalib::asciistream keyName;
    vespalib::asciistream valueName;
    auto leftBracePos = attributeName.find('{');
    auto baseName = attributeName.substr(0, leftBracePos);
    auto rightBracePos = attributeName.rfind('}');
    keyName << baseName << ".key";
    valueName << baseName << ".value" << attributeName.substr(rightBracePos + 1);
    if (rightBracePos != vespalib::string::npos && rightBracePos > leftBracePos) {
        if (attributeName[leftBracePos + 1] == '"' && attributeName[rightBracePos - 1] == '"') {
            vespalib::string key = attributeName.substr(leftBracePos + 2, rightBracePos - leftBracePos - 3);
            return std::make_unique<AttributeMapLookupNode>(attributeName, keyName.str(), valueName.str(), key, "");
        } else if (attributeName.substr(leftBracePos + 1, indirectKeyMarker.size()) == indirectKeyMarker && attributeName[rightBracePos - 1] == ')') {
            auto startPos = leftBracePos + 1 + indirectKeyMarker.size();
            vespalib::string keySourceAttributeName = attributeName.substr(startPos, rightBracePos - 1 - startPos);
            return std::make_unique<AttributeMapLookupNode>(attributeName, keyName.str(), valueName.str(), "", keySourceAttributeName);
        }
    }
    return std::unique_ptr<AttributeNode>();
}

}
