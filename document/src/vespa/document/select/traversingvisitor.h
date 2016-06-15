// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"

namespace document {
namespace select {

class
TraversingVisitor : public Visitor
{
public:
    virtual void
    visitAndBranch(const And &expr);

    virtual void
    visitOrBranch(const Or &expr);

    virtual void
    visitNotBranch(const Not &expr);

    virtual void
    visitComparison(const Compare &expr);

    virtual void
    visitArithmeticValueNode(const ArithmeticValueNode &expr);

    virtual void
    visitFunctionValueNode(const FunctionValueNode &expr);

    virtual void
    visitConstant(const Constant &);

    virtual void
    visitInvalidConstant(const InvalidConstant &);

    virtual void
    visitDocumentType(const DocType &);

    virtual void
    visitIdValueNode(const IdValueNode &);

    virtual void
    visitSearchColumnValueNode(const SearchColumnValueNode &);

    virtual void
    visitFieldValueNode(const FieldValueNode &);

    virtual void
    visitFloatValueNode(const FloatValueNode &);

    virtual void
    visitVariableValueNode(const VariableValueNode &);

    virtual void
    visitIntegerValueNode(const IntegerValueNode &);

    virtual void
    visitCurrentTimeValueNode(const CurrentTimeValueNode &);

    virtual void
    visitStringValueNode(const StringValueNode &);

    virtual void
    visitNullValueNode(const NullValueNode &);

    virtual void
    visitInvalidValueNode(const InvalidValueNode &);
};

}

}

