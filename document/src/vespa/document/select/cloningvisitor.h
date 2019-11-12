// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "visitor.h"
#include "resultset.h"
#include <memory>

namespace document::select {

class Node;
class ValueNode;

class CloningVisitor : public Visitor
{
protected:
    std::unique_ptr<Node> _node;
    std::unique_ptr<ValueNode> _valueNode;
    bool _constVal;
    int _priority;
    uint32_t _fieldNodes;
    ResultSet _resultSet;

    static const int OrPriority = 100;
    static const int AndPriority = 200;
    static const int NotPriority = 300;
    static const int ComparePriority = 400;
    static const int AddPriority = 500;
    static const int SubPriority = 500;
    static const int MulPriority = 600;
    static const int DivPriority = 600;
    static const int ModPriority = 700;
    static const int DocumentTypePriority = 1000;
    static const int FieldValuePriority = 1000;
    static const int InvalidConstPriority = 1000;
    static const int InvalidValPriority = 1000;
    static const int ConstPriority = 1000;
    static const int FuncPriority = 1000;
    static const int VariablePriority = 1000;
    static const int FloatPriority = 1000;
    static const int IntegerPriority = 1000;
    static const int CurrentTimePriority = 1000;
    static const int StringPriority = 1000;
    static const int NullValPriority = 1000;
    static const int IdPriority = 1000;
    static const int SearchColPriority = 1000;

public:
    CloningVisitor();
    ~CloningVisitor();

    void visitAndBranch(const And &expr) override;
    void visitOrBranch(const Or &expr) override;
    void visitNotBranch(const Not &expr) override;
    void visitComparison(const Compare &expr) override;
    void visitArithmeticValueNode(const ArithmeticValueNode &expr) override;
    void visitFunctionValueNode(const FunctionValueNode &expr) override;
    void visitConstant(const Constant &expr) override;
    void visitInvalidConstant(const InvalidConstant &expr) override;
    void visitDocumentType(const DocType &expr) override;
    void visitIdValueNode(const IdValueNode &expr) override;
    void visitFieldValueNode(const FieldValueNode &expr) override;
    void visitFloatValueNode(const FloatValueNode &expr) override;
    void visitVariableValueNode(const VariableValueNode &expr) override;
    void visitIntegerValueNode(const IntegerValueNode &expr) override;
    void visitCurrentTimeValueNode(const CurrentTimeValueNode &expr) override;
    void visitStringValueNode(const StringValueNode &expr) override;
    void visitNullValueNode(const NullValueNode &expr) override;
    void visitInvalidValueNode(const InvalidValueNode &expr) override;

    std::unique_ptr<Node> &getNode() { return _node; }
    std::unique_ptr<ValueNode> &getValueNode() { return _valueNode; }

    void setNodeParentheses(int priority);
    void setValueNodeParentheses(int priority);
    void setArithmeticValueNode(const ArithmeticValueNode &expr, std::unique_ptr<ValueNode> lhs,
                                int lhsPriority, bool lhsConstVal, std::unique_ptr<ValueNode> rhs,
                                int rhsPriority, bool rhsConstVal);

    void swap(CloningVisitor &rhs);
    void revisit(void);
};

}
