// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attributenode.h"

namespace search::expression {

/**
 * Extract map value from attribute for the map key specified in the
 * grouping expression.
 */
class AttributeMapLookupNode : public AttributeNode
{
public:
    using IAttributeVector = search::attribute::IAttributeVector;
    class KeyHandler;
private:
    vespalib::string        _keyAttributeName;
    vespalib::string        _valueAttributeName;
    vespalib::string        _key;
    vespalib::string        _keySourceAttributeName;
    const IAttributeVector *_keyAttribute;
    const IAttributeVector *_keySourceAttribute;

    std::unique_ptr<KeyHandler> makeKeyHandlerHelper() const;
    std::unique_ptr<KeyHandler> makeKeyHandler() const;
    void cleanup() override;
    void wireAttributes(const search::attribute::IAttributeContext & attrCtx) override;
    std::pair<std::unique_ptr<ResultNode>, std::unique_ptr<Handler>>
    createResultHandler(bool preserveAccurateType, const attribute::IAttributeVector & attribute) const override;
public:
    DECLARE_NBO_SERIALIZE;
    DECLARE_EXPRESSIONNODE(AttributeMapLookupNode);
    AttributeMapLookupNode();
    AttributeMapLookupNode(vespalib::stringref name, vespalib::stringref keyAttributeName, vespalib::stringref valueAttributeName, vespalib::stringref key, vespalib::stringref keySourceAttributeName);
    AttributeMapLookupNode(const AttributeMapLookupNode &);
    AttributeMapLookupNode(AttributeMapLookupNode &&) = delete;
    ~AttributeMapLookupNode() override;
    AttributeMapLookupNode &operator=(const AttributeMapLookupNode &rhs);
    AttributeMapLookupNode &operator=(AttributeMapLookupNode &&rhs) = delete;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
};

}
