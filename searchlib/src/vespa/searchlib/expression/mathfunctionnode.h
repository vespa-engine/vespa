// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"

namespace search {
namespace expression {

class MathFunctionNode : public MultiArgFunctionNode
{
public:
    typedef enum {EXP=0, POW=1, LOG=2, LOG1P=3, LOG10=4, SIN=5, ASIN=6, COS=7, ACOS=8, TAN=9, ATAN=10, SQRT=11, SINH=12,
                  ASINH=13, COSH=14, ACOSH=15, TANH=16, ATANH=17, CBRT=18, HYPOT=19, FLOOR=20 } Function;
    DECLARE_EXPRESSIONNODE(MathFunctionNode);
    DECLARE_NBO_SERIALIZE;

    MathFunctionNode() { }
private:
    bool onExecute() const override;
    void onPrepareResult() override;
    Function _function;
};

}
}

