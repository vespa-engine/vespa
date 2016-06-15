// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/multiargfunctionnode.h>

namespace search {
namespace expression {

class StrCatFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(StrCatFunctionNode);
    StrCatFunctionNode() { }
    StrCatFunctionNode(const ExpressionNode & arg) { addArg(arg); }
private:
    virtual void onPrepareResult();
    virtual bool onExecute() const;
};

}
}

