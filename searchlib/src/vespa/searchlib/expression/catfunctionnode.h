// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/multiargfunctionnode.h>

namespace search {
namespace expression {

class CatFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(CatFunctionNode);
    CatFunctionNode() { }
    CatFunctionNode(const ExpressionNode & arg) { addArg(arg); }
private:
    virtual void onPrepare(bool preserveAccurateTypes);
    virtual void onPrepareResult();
    virtual bool onExecute() const;
};

}
}

