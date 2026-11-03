// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_node_replacer.h"

#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/searchcommon/attribute/iattributecontext.h>
#include <vespa/searchlib/aggregation/aggregationresult.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/documentfieldnode.h>
#include <vespa/searchlib/expression/expressiontree.h>
#include <vespa/searchlib/expression/interpolated_document_field_lookup_node.h>
#include <vespa/searchlib/expression/interpolatedlookupfunctionnode.h>
#include <vespa/searchlib/expression/multiargfunctionnode.h>
#include <vespa/searchlib/expression/position_document_field_node.h>

using document::PositionDataType;
using namespace search::expression;

namespace search::aggregation {

bool AttributeNodeReplacer::check(const vespalib::Identifiable& obj) const {
    return obj.getClass().inherits(ExpressionTree::classId) || obj.getClass().inherits(AggregationResult::classId) ||
           obj.getClass().inherits(MultiArgFunctionNode::classId);
}

void AttributeNodeReplacer::replaceRecurse(ExpressionNode* exp, std::function<void(ExpressionNodeUP)>&& modifier) {
    if (exp == nullptr) {
        return;
    }
    if (exp->inherits(AttributeNode::classId)) {
        auto replacementNode = getReplacementNode(static_cast<const AttributeNode&>(*exp));
        if (replacementNode) {
            modifier(std::move(replacementNode));
        }
    } else {
        exp->select(*this, *this);
    }
}

void AttributeNodeReplacer::execute(vespalib::Identifiable& obj) {
    if (obj.getClass().inherits(ExpressionTree::classId)) {
        auto& tree = static_cast<ExpressionTree&>(obj);
        replaceRecurse(tree.getRoot(),
                       [&tree](ExpressionNodeUP replacement) noexcept { tree.changeRoot(std::move(replacement)); });
    } else if (obj.getClass().inherits(AggregationResult::classId)) {
        auto& result = static_cast<AggregationResult&>(obj);
        replaceRecurse(result.getExpression(),
                       [&result](ExpressionNodeUP replacement) { result.setExpression(std::move(replacement)); });
        // Matching this object stops the selection from descending on its own, so visit the members
        // explicitly to reach any additional expression trees held by the aggregation result.
        result.selectMembers(*this, *this);
    } else if (obj.getClass().inherits(MultiArgFunctionNode::classId)) {
        auto& vec = static_cast<MultiArgFunctionNode&>(obj).expressionNodeVector();
        for (auto& expr : vec) {
            replaceRecurse(expr.get(),
                           [&expr](ExpressionNodeUP replacement) noexcept { expr = std::move(replacement); });
        }
    }
}

std::unique_ptr<ExpressionNode> Attribute2DocumentAccessor::getReplacementNode(const AttributeNode& attributeNode) {
    if (attributeNode.inherits(InterpolatedLookup::classId)) {
        auto& interpolated_lookup = static_cast<const InterpolatedLookup&>(attributeNode);
        return std::make_unique<InterpolatedDocumentFieldLookupNode>(interpolated_lookup.getAttributeName(),
                                                                     interpolated_lookup.clone_lookup_expression());
    }
    const auto& name = attributeNode.getAttributeName();
    if (PositionDataType::isZCurveFieldName(name)) {
        auto field_name = std::string(PositionDataType::cutZCurveFieldName(name));
        return std::make_unique<PositionDocumentFieldNode>(field_name);
    }
    return std::make_unique<DocumentFieldNode>(name);
}

std::unique_ptr<ExpressionNode>
NonAttribute2DocumentAccessor::getReplacementNode(const expression::AttributeNode& attributeNode) {
    if (_attrCtx.getAttribute(attributeNode.getAttributeName()) == nullptr) {
        return Attribute2DocumentAccessor::getReplacementNode(attributeNode);
    }
    return {};
}

} // namespace search::aggregation

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_modifiers() {
}
