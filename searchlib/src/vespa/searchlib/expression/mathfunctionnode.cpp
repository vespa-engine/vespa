// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/expression/mathfunctionnode.h>
#include <vespa/searchlib/expression/floatresultnode.h>
#include <math.h>

namespace search {
namespace expression {

using namespace vespalib;

IMPLEMENT_EXPRESSIONNODE(MathFunctionNode, MultiArgFunctionNode);

Serializer & MathFunctionNode::onSerialize(Serializer & os) const
{
    MultiArgFunctionNode::onSerialize(os);
    uint8_t code(_function);
    return os << code;
}

Deserializer & MathFunctionNode::onDeserialize(Deserializer & is)
{
    MultiArgFunctionNode::onDeserialize(is);
    uint8_t code(0);
    is >> code;
    _function = (Function)code;
    return is;
}

void MathFunctionNode::onPrepareResult()
{
    setResultType(std::unique_ptr<ResultNode>(new FloatResultNode()));
}

bool MathFunctionNode::onExecute() const
{
    getArg(0).execute();
    double result(0.0);
    switch (_function) {
    case EXP: result = exp(getArg(0).getResult().getFloat()); break;
    case POW: getArg(1).execute(); result = pow(getArg(0).getResult().getFloat(), getArg(1).getResult().getFloat()); break;
    case LOG: result = log(getArg(0).getResult().getFloat()); break;
    case LOG1P: result = log1p(getArg(0).getResult().getFloat()); break;
    case LOG10: result = log10(getArg(0).getResult().getFloat()); break;
    case SIN: result = sin(getArg(0).getResult().getFloat()); break;
    case ASIN: result = asin(getArg(0).getResult().getFloat()); break;
    case COS: result = cos(getArg(0).getResult().getFloat()); break;
    case ACOS: result = acos(getArg(0).getResult().getFloat()); break;
    case TAN: result = tan(getArg(0).getResult().getFloat()); break;
    case ATAN: result = atan(getArg(0).getResult().getFloat()); break;
    case SQRT: result = sqrt(getArg(0).getResult().getFloat()); break;
    case SINH: result = sinh(getArg(0).getResult().getFloat()); break;
    case ASINH: result = asinh(getArg(0).getResult().getFloat()); break;
    case COSH: result = cosh(getArg(0).getResult().getFloat()); break;
    case ACOSH: result = acosh(getArg(0).getResult().getFloat()); break;
    case TANH: result = tanh(getArg(0).getResult().getFloat()); break;
    case ATANH: result = atanh(getArg(0).getResult().getFloat()); break;
    case CBRT: result = cbrt(getArg(0).getResult().getFloat()); break;
    case HYPOT: getArg(1).execute(); result = hypot(getArg(0).getResult().getFloat(), getArg(1).getResult().getFloat()); break;
    case FLOOR: result = floor(getArg(0).getResult().getFloat()); break;
    }
    static_cast<FloatResultNode &>(updateResult()).set(result);
    return true;
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_mathfunctionnode() {}
