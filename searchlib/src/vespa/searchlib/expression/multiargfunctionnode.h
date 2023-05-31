// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "functionnode.h"

namespace search::expression {

class MultiArgFunctionNode : public FunctionNode
{
public:
    using ExpressionNodeVector = std::vector<ExpressionNode::CP>;
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor & visitor) const override;
    DECLARE_ABSTRACT_EXPRESSIONNODE(MultiArgFunctionNode);
    MultiArgFunctionNode() noexcept;
    MultiArgFunctionNode(const MultiArgFunctionNode &);
    MultiArgFunctionNode & operator = (const MultiArgFunctionNode &);
    MultiArgFunctionNode(MultiArgFunctionNode &&) noexcept = default;
    MultiArgFunctionNode & operator = (MultiArgFunctionNode &&) noexcept = default;
    ~MultiArgFunctionNode();
    MultiArgFunctionNode & appendArg(ExpressionNode::UP arg) { return addArg(std::move(arg)); }
    MultiArgFunctionNode & addArg(ExpressionNode::UP arg) {
        _args.emplace_back(arg.release());
        return *this;
    }
    void reset() override { _args.clear(); FunctionNode::reset(); }
    ExpressionNodeVector & expressionNodeVector() { return _args; }
protected:
    virtual bool onCalculate(const ExpressionNodeVector & args, ResultNode & result) const;
    bool onExecute() const override;
    void onPrepare(bool preserveAccurateTypes) override;
    size_t getNumArgs() const { return _args.size(); }
    const ExpressionNode & getArg(size_t n) const { return *_args[n]; }
    ExpressionNode &       getArg(size_t n)       { return *_args[n]; }
private:
    void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation) override;
    bool calculate(const ExpressionNodeVector & args, ResultNode & result) const { return onCalculate(args, result); }
    void prepareResult() { onPrepareResult(); }
    virtual void onPrepareResult();
    ExpressionNodeVector _args;
};

}
