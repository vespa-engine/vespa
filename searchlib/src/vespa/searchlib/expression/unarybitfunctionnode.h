// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace search::expression {

class UnaryBitFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_NBO_SERIALIZE;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    DECLARE_ABSTRACT_EXPRESSIONNODE(UnaryBitFunctionNode);
    UnaryBitFunctionNode() : _numBits(0) { }
    UnaryBitFunctionNode(ExpressionNode::UP arg, unsigned numBits) : UnaryFunctionNode(std::move(arg)), _numBits(numBits) { }
    ~UnaryBitFunctionNode() override;
protected:
    size_t getNumBits()  const { return _numBits; }
    size_t getNumBytes() const { return (_numBits+7)/8; }
    void onPrepareResult() override;
private:
    void onPrepare(bool preserveAccurateTypes) override;
    virtual bool internalExecute(const vespalib::nbostream & os) const = 0;
    bool onExecute() const override;
    uint32_t _numBits;
    mutable vespalib::nbostream _tmpOs;
};

}
