// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unaryfunctionnode.h>

namespace search {
namespace expression {

class UnaryBitFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;
    DECLARE_ABSTRACT_EXPRESSIONNODE(UnaryBitFunctionNode);
    UnaryBitFunctionNode() : _numBits(0) { }
    UnaryBitFunctionNode(const ExpressionNode::CP & arg, unsigned numBits) : UnaryFunctionNode(arg), _numBits(numBits) { }
protected:
    size_t getNumBits()  const { return _numBits; }
    size_t getNumBytes() const { return (_numBits+7)/8; }
    virtual void onPrepareResult();
private:
    virtual void onPrepare(bool preserveAccurateTypes);
    virtual bool internalExecute(const vespalib::nbostream & os) const = 0;
    virtual bool onExecute() const;
    uint32_t _numBits;
    mutable vespalib::nbostream _tmpOs;
};

}
}

