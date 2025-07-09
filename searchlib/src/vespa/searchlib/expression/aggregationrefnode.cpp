// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "aggregationrefnode.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>

namespace search {
namespace expression {

using namespace vespalib;

IMPLEMENT_EXPRESSIONNODE(AggregationRefNode, ExpressionNode);

AggregationRefNode::AggregationRefNode(const AggregationRefNode & rhs) :
    ExpressionNode(),
    _index(rhs._index),
    _expressionNode(nullptr)
{
}

AggregationRefNode & AggregationRefNode::operator = (const AggregationRefNode & expr)
{
    if (this != &expr) {
        _index = expr._index;
        _expressionNode = nullptr;
    }
    return *this;
}

bool AggregationRefNode::onExecute() const
{
    if (_expressionNode != nullptr) {
        return _expressionNode->execute();
    }
    return false;
}

void AggregationRefNode::locateExpression(ExpressionNodeArray & exprVec) const
{
    if (_expressionNode == nullptr) {
        _expressionNode = static_cast<ExpressionNode *>(exprVec[_index].get());
        if (_expressionNode == nullptr) {
            throw std::runtime_error(make_string("Failed locating expression for index '%d'", _index));
        }
    }
}

Serializer & AggregationRefNode::onSerialize(Serializer & os) const
{
    return os << _index;
}

Deserializer & AggregationRefNode::onDeserialize(Deserializer & is)
{
    return is >> _index;
}

void
AggregationRefNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "index", _index);
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_expressionrefnode() {}
