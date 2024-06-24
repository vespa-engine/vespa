// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "modifiers.h"
#include "grouping.h"
#include <vespa/searchlib/expression/multiargfunctionnode.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/documentfieldnode.h>
#include <vespa/searchlib/expression/interpolated_document_field_lookup_node.h>
#include <vespa/searchlib/expression/interpolatedlookupfunctionnode.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>

using namespace search::expression;

namespace search::aggregation {

bool
AttributeNodeReplacer::check(const vespalib::Identifiable &obj) const
{
    return obj.getClass().inherits(GroupingLevel::classId) || obj.getClass().inherits(AggregationResult::classId) || obj.getClass().inherits(MultiArgFunctionNode::classId);
}

void
AttributeNodeReplacer::replaceRecurse(ExpressionNode * exp, std::function<void(ExpressionNodeUP)> && modifier) {
    if (exp == nullptr) return;
    if (exp->inherits(AttributeNode::classId)) {
        auto replacementNode = getReplacementNode(static_cast<const AttributeNode &>(*exp));
        if (replacementNode) {
            modifier(std::move(replacementNode));
        }
    } else {
        exp->select(*this, *this);
    }
}

void
AttributeNodeReplacer::execute(vespalib::Identifiable &obj)
{
    if (obj.getClass().inherits(GroupingLevel::classId)) {
        auto & g(static_cast<GroupingLevel &>(obj));
        replaceRecurse(g.getExpression().getRoot(), [&g](ExpressionNodeUP replacement) { g.setExpression(std::move(replacement)); });
        g.groupPrototype().select(*this, *this);
    } else if(obj.getClass().inherits(AggregationResult::classId)) {
        auto & a(static_cast<AggregationResult &>(obj));
        replaceRecurse(a.getExpression(), [&a](ExpressionNodeUP replacement) { a.setExpression(std::move(replacement)); });
    } else if(obj.getClass().inherits(MultiArgFunctionNode::classId)) {
        MultiArgFunctionNode::ExpressionNodeVector & v(static_cast<MultiArgFunctionNode &>(obj).expressionNodeVector());
        for (auto & e : v) {
            replaceRecurse(e.get(), [&e](ExpressionNodeUP replacement) noexcept { e = std::move(replacement); });
        }
    }
}

std::unique_ptr<ExpressionNode>
Attribute2DocumentAccessor::getReplacementNode(const AttributeNode &attributeNode)
{
    if (attributeNode.inherits(InterpolatedLookup::classId)) {
        auto& interpolated_lookup = static_cast<const InterpolatedLookup&>(attributeNode);
        return std::make_unique<InterpolatedDocumentFieldLookupNode>(interpolated_lookup.getAttributeName(), interpolated_lookup.clone_lookup_expression());
    }
    return std::make_unique<DocumentFieldNode>(attributeNode.getAttributeName());
}

std::unique_ptr<ExpressionNode>
NonAttribute2DocumentAccessor::getReplacementNode(const expression::AttributeNode &attributeNode) {
    if (_attrCtx.getAttribute(attributeNode.getAttributeName()) == nullptr) {
        return Attribute2DocumentAccessor::getReplacementNode(attributeNode);
    }
    return {};
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_modifiers() {}
