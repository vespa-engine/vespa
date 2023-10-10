// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/objectoperation.h>
#include <vespa/vespalib/objects/objectpredicate.h>
#include <memory>

namespace search::expression {

class ExpressionNode;
class AttributeNode;

}

namespace search::aggregation {

class AttributeNodeReplacer : public vespalib::ObjectOperation, public vespalib::ObjectPredicate
{
private:
    void execute(vespalib::Identifiable &obj) override;
    bool check(const vespalib::Identifiable &obj) const override;
    virtual std::unique_ptr<search::expression::ExpressionNode> getReplacementNode(const search::expression::AttributeNode &attributeNode) = 0;
};

class Attribute2DocumentAccessor : public AttributeNodeReplacer
{
private:
    std::unique_ptr<search::expression::ExpressionNode> getReplacementNode(const search::expression::AttributeNode &attributeNode) override;
};

}
