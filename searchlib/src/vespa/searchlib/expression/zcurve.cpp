// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "zcurve.h"
#include <vespa/vespalib/geo/zcurve.h>

using vespalib::FieldBase;
using vespalib::Serializer;
using vespalib::Deserializer;

namespace search {
namespace expression {

IMPLEMENT_EXPRESSIONNODE(ZCurveFunctionNode, UnaryFunctionNode);

ZCurveFunctionNode::~ZCurveFunctionNode() {}

ZCurveFunctionNode::ZCurveFunctionNode(const ZCurveFunctionNode & rhs) :
    UnaryFunctionNode(rhs),
    _dim(rhs._dim),
    _handler()
{
}

ZCurveFunctionNode & ZCurveFunctionNode::operator = (const ZCurveFunctionNode & rhs)
{
    if (this != &rhs) {
        UnaryFunctionNode::operator =(rhs);
        _dim = rhs._dim;
        _handler.reset();
    }
    return *this;
}

void ZCurveFunctionNode::onPrepareResult()
{
    if (getArg().getResult().inherits(ResultNodeVector::classId)) {
        setResultType(std::unique_ptr<ResultNode>(new IntegerResultNodeVector));
        _handler.reset(new MultiValueHandler(*this));
    } else {
        setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode));
        _handler.reset(new SingleValueHandler(*this));
    }
}

int32_t ZCurveFunctionNode::Handler::getXorY(uint64_t z) const
{
    int32_t x, y;
    vespalib::geo::ZCurve::decode(z, &x, &y);
    return (_dim==X) ? x : y;
}

bool ZCurveFunctionNode::onExecute() const
{
    getArg().execute();
    _handler->handle(getArg().getResult());
    return true;
}

void ZCurveFunctionNode::SingleValueHandler::handle(const ResultNode & arg)
{
    handleOne(arg, _result);
}

void ZCurveFunctionNode::MultiValueHandler::handle(const ResultNode & arg)
{
    const ResultNodeVector & v(static_cast<const ResultNodeVector &>(arg));
   _result.getVector().resize(v.size());
    for(size_t i(0), m(_result.getVector().size()); i < m; i++) {
        handleOne(v.get(i), _result.getVector()[i]);
    }
}

Serializer & ZCurveFunctionNode::onSerialize(Serializer & os) const
{
    UnaryFunctionNode::onSerialize(os);
    uint8_t code(_dim);
    return os << code;
}

Deserializer & ZCurveFunctionNode::onDeserialize(Deserializer & is)
{
    UnaryFunctionNode::onDeserialize(is);
    uint8_t code(0);
    is >> code;
    _dim = static_cast<Dimension>(code);
    return is;
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_zcurve() {}
