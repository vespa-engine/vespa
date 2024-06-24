// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <memory>
#include <functional>

namespace search::expression {

class ExpressionNode;
class AttributeNode;

}

namespace search::aggregation {

class AttributeNodeReplacer : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
{
protected:
    using ExpressionNodeUP = std::unique_ptr<expression::ExpressionNode>;
private:
    void replaceRecurse(expression::ExpressionNode * exp, std::function<void(ExpressionNodeUP)> && modifier);
    void execute(vespalib::Identifiable &obj) override;
    bool check(const vespalib::Identifiable &obj) const override;
    virtual ExpressionNodeUP getReplacementNode(const expression::AttributeNode &attributeNode) = 0;
};

class Attribute2DocumentAccessor : public AttributeNodeReplacer
{
private:
    ExpressionNodeUP getReplacementNode(const expression::AttributeNode &attributeNode) override;
};

}
