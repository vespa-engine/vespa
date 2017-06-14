// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "modifiers.h"
#include "grouping.h"
#include <vespa/searchlib/expression/multiargfunctionnode.h>
#include <vespa/searchlib/expression/attributenode.h>
#include <vespa/searchlib/expression/documentfieldnode.h>

using namespace search::expression;

namespace search {
namespace aggregation {

bool Attribute2DocumentAccessor::check(const vespalib::Identifiable &obj) const
{
    return obj.getClass().inherits(GroupingLevel::classId) || obj.getClass().inherits(AggregationResult::classId) || obj.getClass().inherits(MultiArgFunctionNode::classId);
}

void Attribute2DocumentAccessor::execute(vespalib::Identifiable &obj)
{
    if (obj.getClass().inherits(GroupingLevel::classId)) {
        GroupingLevel & g(static_cast<GroupingLevel &>(obj));
        if (g.getExpression().getRoot()->inherits(AttributeNode::classId)) {
            g.setExpression(std::make_unique<DocumentFieldNode>(static_cast<const AttributeNode &>(*g.getExpression().getRoot()).getAttributeName()));
        } else {
            g.getExpression().getRoot()->select(*this, *this);
        }
        g.groupPrototype().select(*this, *this);
    } else if(obj.getClass().inherits(AggregationResult::classId)) {
        AggregationResult & a(static_cast<AggregationResult &>(obj));
        ExpressionNode * e(a.getExpression());
        if (e) {
            if (e->inherits(AttributeNode::classId)) {
                a.setExpression(std::make_unique<DocumentFieldNode>(static_cast<const AttributeNode &>(*e).getAttributeName()));
            } else {
                e->select(*this, *this);
            }
        }
    } else if(obj.getClass().inherits(MultiArgFunctionNode::classId)) {
        MultiArgFunctionNode::ExpressionNodeVector & v(static_cast<MultiArgFunctionNode &>(obj).expressionNodeVector());
        for(size_t i(0), m(v.size()); i < m; i++) {
            ExpressionNode::CP & e(v[i]);
            if (e->inherits(AttributeNode::classId)) {
                e.reset(new DocumentFieldNode(static_cast<const AttributeNode &>(*e).getAttributeName()));
            } else {
                e->select(*this, *this);
            }
        }
    }
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_modifiers() {}
