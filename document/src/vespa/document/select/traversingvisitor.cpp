// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "traversingvisitor.h"
#include "valuenodes.h"
#include "branch.h"
#include "compare.h" 

namespace document::select {

void
TraversingVisitor::visitAndBranch(const And &expr)
{
    expr.getLeft().visit(*this); expr.getRight().visit(*this);
};


void
TraversingVisitor::visitOrBranch(const Or &expr)
{
    expr.getLeft().visit(*this); expr.getRight().visit(*this);
};


void
TraversingVisitor::visitNotBranch(const Not &expr)
{
    expr.getChild().visit(*this);
};


void
TraversingVisitor::visitComparison(const Compare &expr)
{
    expr.getLeft().visit(*this); expr.getRight().visit(*this);
};


void
TraversingVisitor::visitArithmeticValueNode(const ArithmeticValueNode &expr)
{
    expr.getLeft().visit(*this); expr.getRight().visit(*this);
}


void
TraversingVisitor::visitFunctionValueNode(const FunctionValueNode &expr) {
    expr.getChild().visit(*this);
};


void
TraversingVisitor::visitConstant(const Constant &)
{
}


void
TraversingVisitor::visitInvalidConstant(const InvalidConstant &)
{
}


void
TraversingVisitor::visitDocumentType(const DocType &)
{
}


void
TraversingVisitor::visitIdValueNode(const IdValueNode &)
{
}


void
TraversingVisitor::visitFieldValueNode(const FieldValueNode &)
{
}


void
TraversingVisitor::visitFloatValueNode(const FloatValueNode &)
{
}


void
TraversingVisitor::visitVariableValueNode(const VariableValueNode &)
{
}


void
TraversingVisitor::visitIntegerValueNode(const IntegerValueNode &)
{
}


void
TraversingVisitor::visitBoolValueNode(const BoolValueNode &)
{
}


void
TraversingVisitor::visitCurrentTimeValueNode(const CurrentTimeValueNode &)
{
}


void
TraversingVisitor::visitStringValueNode(const StringValueNode &)
{
}


void
TraversingVisitor::visitNullValueNode(const NullValueNode &)
{
}

void
TraversingVisitor::visitInvalidValueNode(const InvalidValueNode &)
{
}

}
