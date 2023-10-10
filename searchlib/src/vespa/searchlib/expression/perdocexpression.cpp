// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "relevancenode.h"

namespace search {
namespace expression {

using namespace vespalib;

IMPLEMENT_EXPRESSIONNODE(RelevanceNode,        ExpressionNode);

void
RelevanceNode::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "Value", _relevance);
}

Serializer & RelevanceNode::onSerialize(Serializer & os) const
{
    return _relevance.serialize(os);
}

Deserializer & RelevanceNode::onDeserialize(Deserializer & is)
{
    return _relevance.deserialize(is);
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_perdocexpression() {}
