// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"

namespace document::select {

class
TraversingVisitor : public Visitor
{
public:
    void visitAndBranch(const And &expr) override;

    void visitOrBranch(const Or &expr) override;
    void visitNotBranch(const Not &expr) override;
    void visitComparison(const Compare &expr) override;
    void visitArithmeticValueNode(const ArithmeticValueNode &expr) override;
    void visitFunctionValueNode(const FunctionValueNode &expr) override;
    void visitConstant(const Constant &) override;
    void visitInvalidConstant(const InvalidConstant &) override;
    void visitDocumentType(const DocType &) override;
    void visitIdValueNode(const IdValueNode &) override;
    void visitFieldValueNode(const FieldValueNode &) override;
    void visitFloatValueNode(const FloatValueNode &) override;
    void visitVariableValueNode(const VariableValueNode &) override;
    void visitIntegerValueNode(const IntegerValueNode &) override;
    void visitBoolValueNode(const BoolValueNode &) override;
    void visitCurrentTimeValueNode(const CurrentTimeValueNode &) override;
    void visitStringValueNode(const StringValueNode &) override;
    void visitNullValueNode(const NullValueNode &) override;
    void visitInvalidValueNode(const InvalidValueNode &) override;
};

}
