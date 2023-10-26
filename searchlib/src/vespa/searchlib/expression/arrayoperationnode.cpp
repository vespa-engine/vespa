// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "arrayoperationnode.h"
#include <vespa/vespalib/util/stringfmt.h>

namespace search {
namespace expression {

IMPLEMENT_ABSTRACT_EXPRESSIONNODE(ArrayOperationNode, FunctionNode);

ArrayOperationNode::ArrayOperationNode()
  : FunctionNode(), _attributeName(), _attribute(0), _docId(0)
{}

ArrayOperationNode::ArrayOperationNode(const ArrayOperationNode& rhs)
  : FunctionNode(),
    _attributeName(rhs._attributeName),
    _attribute(rhs._attribute),
    _docId(0)
{}

// for unit testing
ArrayOperationNode::ArrayOperationNode(IAttributeVector &attr)
  : FunctionNode(),
    _attributeName(attr.getName()),
    _attribute(&attr),
    _docId(0)
{}

ArrayOperationNode&
ArrayOperationNode::operator= (const ArrayOperationNode& rhs)
{
        _attributeName = rhs._attributeName;
        _attribute = rhs._attribute;
        _docId = 0;
        return *this;
}

void
ArrayOperationNode::wireAttributes(const IAttributeContext &attrCtx)
{
    _attribute = attrCtx.getAttribute(_attributeName);
    if (_attribute == NULL) {
        throw std::runtime_error(vespalib::make_string("Failed locating attribute vector '%s'", _attributeName.c_str()));
    }
}

using vespalib::Serializer;
using vespalib::Deserializer;

Serializer & ArrayOperationNode::onSerialize(Serializer & os) const
{
    FunctionNode::onSerialize(os);
    os << _attributeName;
    return os;
}

Deserializer & ArrayOperationNode::onDeserialize(Deserializer & is)
{
    FunctionNode::onDeserialize(is);
    is >> _attributeName;
    return is;
}

} // namespace expression
} // namespace search
