// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mathfunctionnode.h"
#include "floatresultnode.h"
#include <cmath>

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
    case EXP: result = std::exp(getArg(0).getResult()->getFloat()); break;
    case POW: getArg(1).execute(); result = std::pow(getArg(0).getResult()->getFloat(), getArg(1).getResult()->getFloat()); break;
    case LOG: result = std::log(getArg(0).getResult()->getFloat()); break;
    case LOG1P: result = std::log1p(getArg(0).getResult()->getFloat()); break;
    case LOG10: result = std::log10(getArg(0).getResult()->getFloat()); break;
    case SIN: result = std::sin(getArg(0).getResult()->getFloat()); break;
    case ASIN: result = std::asin(getArg(0).getResult()->getFloat()); break;
    case COS: result = std::cos(getArg(0).getResult()->getFloat()); break;
    case ACOS: result = std::acos(getArg(0).getResult()->getFloat()); break;
    case TAN: result = std::tan(getArg(0).getResult()->getFloat()); break;
    case ATAN: result = std::atan(getArg(0).getResult()->getFloat()); break;
    case SQRT: result = std::sqrt(getArg(0).getResult()->getFloat()); break;
    case SINH: result = std::sinh(getArg(0).getResult()->getFloat()); break;
    case ASINH: result = std::asinh(getArg(0).getResult()->getFloat()); break;
    case COSH: result = std::cosh(getArg(0).getResult()->getFloat()); break;
    case ACOSH: result = std::acosh(getArg(0).getResult()->getFloat()); break;
    case TANH: result = std::tanh(getArg(0).getResult()->getFloat()); break;
    case ATANH: result = std::atanh(getArg(0).getResult()->getFloat()); break;
    case CBRT: result = std::cbrt(getArg(0).getResult()->getFloat()); break;
    case HYPOT: getArg(1).execute(); result = std::hypot(getArg(0).getResult()->getFloat(), getArg(1).getResult()->getFloat()); break;
    case FLOOR: result = std::floor(getArg(0).getResult()->getFloat()); break;
    }
    static_cast<FloatResultNode &>(updateResult()).set(result);
    return true;
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_mathfunctionnode() {}
