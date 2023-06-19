// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "arrayatlookupfunctionnode.h"

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
    // Here we are doing a dirty skipping AttributeNode in the inheritance.
    // This is due to refactoring and the need to keep serialization the same.
    FunctionNode::onSerialize(os);
    os << uint32_t(1u) << _indexExpression; // Simulating a single element vector.
    os << _attributeName;
    return os;
}

Deserializer &
ArrayAtLookup::onDeserialize(Deserializer & is)
{
    // See comment in onSerialize method.
    FunctionNode::onDeserialize(is);
    uint32_t count(0);
    is >> count;
    if (count > 0) {
        is >> _indexExpression;
    } else {
        _indexExpression.reset();
    }
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
        _indexExpression->select(predicate, operation);
    }
}

}
