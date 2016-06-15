// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/functionnode.h>

namespace search {
namespace expression {

class MultiArgFunctionNode : public FunctionNode
{
public:
    typedef std::vector<ExpressionNode::CP> ExpressionNodeVector;
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor & visitor) const;
    DECLARE_ABSTRACT_EXPRESSIONNODE(MultiArgFunctionNode);
    MultiArgFunctionNode() : FunctionNode() { }
    MultiArgFunctionNode & appendArg(const ExpressionNode::CP & arg) { return addArg(arg); }
    MultiArgFunctionNode &addArg(const ExpressionNode::CP & arg) {
        _args.push_back(arg);
        return *this;
    }
    virtual void reset() { _args.clear(); FunctionNode::reset(); }
    ExpressionNodeVector & expressionNodeVector() { return _args; }
protected:
    virtual bool onCalculate(const ExpressionNodeVector & args, ResultNode & result) const;
    virtual bool onExecute() const;
    virtual void onPrepare(bool preserveAccurateTypes);
    size_t getNumArgs() const { return _args.size(); }
    const ExpressionNode & getArg(size_t n) const { return *_args[n]; }
    ExpressionNode &       getArg(size_t n)       { return *_args[n]; }
private:
    virtual void selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation);
    bool calculate(const ExpressionNodeVector & args, ResultNode & result) const { return onCalculate(args, result); }
    void prepareResult() { onPrepareResult(); }
    virtual void onPrepareResult();
    ExpressionNodeVector _args;
};

}
}

