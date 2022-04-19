// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    static constexpr int OrPriority = 100;
    static constexpr int AndPriority = 200;
    static constexpr int NotPriority = 300;
    static constexpr int ComparePriority = 400;
    static constexpr int AddPriority = 500;
    static constexpr int SubPriority = 500;
    static constexpr int MulPriority = 600;
    static constexpr int DivPriority = 600;
    static constexpr int ModPriority = 700;
    static constexpr int DocumentTypePriority = 1000;
    static constexpr int FieldValuePriority = 1000;
    static constexpr int InvalidConstPriority = 1000;
    static constexpr int InvalidValPriority = 1000;
    static constexpr int ConstPriority = 1000;
    static constexpr int FuncPriority = 1000;
    static constexpr int VariablePriority = 1000;
    static constexpr int FloatPriority = 1000;
    static constexpr int IntegerPriority = 1000;
    static constexpr int BoolPriority = 1000;
    static constexpr int CurrentTimePriority = 1000;
    static constexpr int StringPriority = 1000;
    static constexpr int NullValPriority = 1000;
    static constexpr int IdPriority = 1000;

public:
    CloningVisitor();
    ~CloningVisitor() override;

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
    void visitBoolValueNode(const BoolValueNode &expr) override;
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
    void revisit();
};

}
