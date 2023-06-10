// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "arrayatlookupfunctionnode.h"
#include <vespa/vespalib/objects/serializer.hpp>
#include <vespa/vespalib/objects/deserializer.hpp>

namespace search::expression {

using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_EXPRESSIONNODE(ArrayAtLookup, AttributeNode);

ArrayAtLookup::ArrayAtLookup() noexcept
    : AttributeNode(),
      _currentIndex(),
      _indexExpression()
{
    setCurrentIndex(&_currentIndex);
}

ArrayAtLookup::~ArrayAtLookup() = default;
ArrayAtLookup & ArrayAtLookup::operator=(const ArrayAtLookup &rhs) = default;

ArrayAtLookup::ArrayAtLookup(const vespalib::string &attribute, ExpressionNode::UP indexExpr)
    : AttributeNode(attribute),
      _currentIndex(),
      _indexExpression(std::move(indexExpr))
{
    setCurrentIndex(&_currentIndex);
}

ArrayAtLookup::ArrayAtLookup(const search::attribute::IAttributeVector &attr,
                             ExpressionNode::UP indexExpr)
    : AttributeNode(attr),
      _currentIndex(),
      _indexExpression(std::move(indexExpr))
{
    setCurrentIndex(&_currentIndex);
}

ArrayAtLookup::ArrayAtLookup(const ArrayAtLookup &rhs)
    : AttributeNode(rhs),
      _currentIndex(),
      _indexExpression(rhs._indexExpression)
{
    setCurrentIndex(&_currentIndex);
}

bool
ArrayAtLookup::onExecute() const
{
    _indexExpression->execute();
    int64_t idx = _indexExpression->getResult()->getInteger();
    _currentIndex.set(idx);
    AttributeNode::onExecute();
    return true;
}

Serializer &
ArrayAtLookup::onSerialize(Serializer & os) const
{
    FunctionNode::onSerialize(os);
    std::vector<ExpressionNode::CP> args;
    args.emplace_back(_indexExpression);
    os << args;
    os << _attributeName;
    return os;
}

Deserializer &
ArrayAtLookup::onDeserialize(Deserializer & is)
{
    FunctionNode::onDeserialize(is);
    std::vector<ExpressionNode::CP> args;
    is >> args;
    _indexExpression = std::move(args[0]);
    is >> _attributeName;
    return is;
}

void
ArrayAtLookup::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AttributeNode::visitMembers(visitor);
    visit(visitor, "index", *_indexExpression);
}

void
ArrayAtLookup::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    AttributeNode::selectMembers(predicate, operation);
    if (_indexExpression) {
        _indexExpression->selectMembers(predicate, operation);
    }
}

}
