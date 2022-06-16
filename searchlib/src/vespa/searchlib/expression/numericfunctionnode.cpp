// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "numericfunctionnode.h"
#include <stdexcept>

namespace search::expression {

IMPLEMENT_ABSTRACT_EXPRESSIONNODE(NumericFunctionNode,  MultiArgFunctionNode);

NumericFunctionNode::NumericFunctionNode() = default;
NumericFunctionNode::~NumericFunctionNode() = default;

NumericFunctionNode::NumericFunctionNode(const NumericFunctionNode & rhs) :
    MultiArgFunctionNode(rhs),
    _handler()
{
}

NumericFunctionNode & NumericFunctionNode::operator = (const NumericFunctionNode & rhs)
{
    if (this != &rhs) {
        MultiArgFunctionNode::operator =(rhs);
        _handler.reset();
    }
    return *this;
}

void NumericFunctionNode::onPrepare(bool preserveAccurateTypes)
{
    MultiArgFunctionNode::onPrepare(preserveAccurateTypes);
    if (getNumArgs() == 1) {
        if (getArg(0).getResult()->getClass().inherits(IntegerResultNodeVector::classId)) {
            _handler.reset(new FlattenIntegerHandler(*this));
        } else if (getArg(0).getResult()->getClass().inherits(FloatResultNodeVector::classId)) {
            _handler.reset(new FlattenFloatHandler(*this));
        } else if (getArg(0).getResult()->getClass().inherits(StringResultNodeVector::classId)) {
            _handler.reset(new FlattenStringHandler(*this));
        } else {
            throw std::runtime_error(vespalib::string("No FlattenHandler for ") + getArg(0).getResult()->getClass().name());
        }
    } else {
        if (getResult()->getClass().inherits(IntegerResultNodeVector::classId)) {
            _handler.reset(new VectorIntegerHandler(*this));
        } else if (getResult()->getClass().inherits(FloatResultNodeVector::classId)) {
            _handler.reset(new VectorFloatHandler(*this));
        } else if (getResult()->getClass().inherits(StringResultNodeVector::classId)) {
            _handler.reset(new VectorStringHandler(*this));
        } else if (getResult()->getClass().inherits(IntegerResultNode::classId)) {
            _handler.reset(new ScalarIntegerHandler(*this));
        } else if (getResult()->getClass().inherits(FloatResultNode::classId)) {
            _handler.reset(new ScalarFloatHandler(*this));
        } else if (getResult()->getClass().inherits(StringResultNode::classId)) {
            _handler.reset(new ScalarStringHandler(*this));
        } else if (getResult()->getClass().inherits(RawResultNode::classId)) {
            _handler.reset(new ScalarRawHandler(*this));
        } else {
            throw std::runtime_error(vespalib::make_string("NumericFunctionNode::onPrepare does not handle results of type %s", getResult()->getClass().name()));
        }
    }
}

bool NumericFunctionNode::onCalculate(const ExpressionNodeVector & args, ResultNode & result) const
{
    bool retval(true);
    (void) result;
    _handler->handleFirst(*args[0]->getResult());
    for (size_t i(1), m(args.size()); i < m; i++) {
        _handler->handle(*args[i]->getResult());
    }
    return retval;
}

template <typename T>
void NumericFunctionNode::VectorHandler<T>::handle(const ResultNode & arg)
{
    typename T::Vector & result = _result.getVector();
    if (arg.getClass().inherits(ResultNodeVector::classId)) {
        const ResultNodeVector & av = static_cast<const ResultNodeVector &> (arg);
        const size_t argSize(av.size());
        const size_t oldRSize(result.size());
        if (argSize > oldRSize) {
            result.resize(argSize);
            for (size_t i(oldRSize); i < argSize; i++) {
                result[i] = result[i%oldRSize];
            }
        }
        for (size_t i(0), m(result.size()), isize(argSize); i < m; i++) {
            function().executeIterative(av.get(i%isize), result[i]);
        }
    } else {
        for (size_t i(0), m(result.size()); i < m; i++) {
            function().executeIterative(arg, result[i]);
        }
    }
}

template <typename T>
void NumericFunctionNode::VectorHandler<T>::handleFirst(const ResultNode & arg)
{
    typename T::Vector & result = _result.getVector();
    if (arg.getClass().inherits(ResultNodeVector::classId)) {
        const ResultNodeVector & av = static_cast<const ResultNodeVector &> (arg);
        result.resize(av.size());
        for (size_t i(0), m(result.size()); i < m; i++) {
            result[i].set(av.get(i));
        }
    } else {
        result.resize(1);
        result[0].set(arg);
    }
}


void NumericFunctionNode::ScalarIntegerHandler::handle(const ResultNode & arg)
{
    function().executeIterative(arg, _result);
}

void NumericFunctionNode::ScalarFloatHandler::handle(const ResultNode & arg)
{
    function().executeIterative(arg, _result);
}

void NumericFunctionNode::ScalarStringHandler::handle(const ResultNode & arg)
{
    function().executeIterative(arg, _result);
}

void NumericFunctionNode::ScalarRawHandler::handle(const ResultNode & arg)
{
    function().executeIterative(arg, _result);
}

void NumericFunctionNode::FlattenIntegerHandler::handle(const ResultNode & arg)
{
    _result.set(_initial);
    function().flatten(static_cast<const ResultNodeVector &> (arg), _result);
}

void NumericFunctionNode::FlattenFloatHandler::handle(const ResultNode & arg)
{
    _result.set(_initial);
    function().flatten(static_cast<const ResultNodeVector &> (arg), _result);
}

void NumericFunctionNode::FlattenStringHandler::handle(const ResultNode & arg)
{
    _result.set(_initial);
    function().flatten(static_cast<const ResultNodeVector &> (arg), _result);
}

}

// this function was added by ../../forcelink.sh

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_numericfunctionnode() {}
