// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "timestamp.h"

namespace search::expression {

using vespalib::Serializer;
using vespalib::Deserializer;

IMPLEMENT_EXPRESSIONNODE(TimeStampFunctionNode, UnaryFunctionNode);

TimeStampFunctionNode::TimeStampFunctionNode()
    : _timePart(Year),
      _isGmt(true)
{ }
TimeStampFunctionNode::TimeStampFunctionNode(ExpressionNode::UP arg, TimePart timePart, bool gmt)
    : UnaryFunctionNode(std::move(arg)),
      _timePart(timePart),
      _isGmt(gmt)
{ }
TimeStampFunctionNode::~TimeStampFunctionNode() = default;

TimeStampFunctionNode::TimeStampFunctionNode(const TimeStampFunctionNode & rhs) :
    UnaryFunctionNode(rhs),
    _timePart(rhs._timePart),
    _isGmt(rhs._isGmt),
    _handler()
{
}

TimeStampFunctionNode & TimeStampFunctionNode::operator = (const TimeStampFunctionNode & rhs)
{
    if (this != &rhs) {
        UnaryFunctionNode::operator =(rhs);
        _timePart = rhs._timePart;
        _isGmt = rhs._isGmt;
        _handler.reset();
    }
    return *this;
}

void TimeStampFunctionNode::onPrepareResult()
{
    if (getArg().getResult()->inherits(ResultNodeVector::classId)) {
        setResultType(std::unique_ptr<ResultNode>(new IntegerResultNodeVector));
        _handler.reset(new MultiValueHandler(*this));
    } else {
        setResultType(std::unique_ptr<ResultNode>(new Int64ResultNode));
        _handler.reset(new SingleValueHandler(*this));
    }
}

unsigned TimeStampFunctionNode::getTimePart(time_t secSince70, TimePart tp, bool gmt)
{
    tm ts;
    if (gmt) {
        gmtime_r(&secSince70, &ts);
    } else {
        localtime_r(&secSince70, &ts);
    }
    switch (tp) {
        case Year:    return ts.tm_year + 1900;
        case Month:   return ts.tm_mon + 1;
        case MonthDay:return ts.tm_mday;
        case WeekDay: return ts.tm_wday;
        case Hour:    return ts.tm_hour;
        case Minute:  return ts.tm_min;
        case Second:  return ts.tm_sec;
        case YearDay: return ts.tm_yday;
        case IsDST:   return ts.tm_isdst;
    }
    return 0;
}

bool TimeStampFunctionNode::onExecute() const
{
    getArg().execute();
    _handler->handle(*getArg().getResult());
    return true;
}

void TimeStampFunctionNode::SingleValueHandler::handle(const ResultNode & arg)
{
    handleOne(arg, _result);
}

void TimeStampFunctionNode::MultiValueHandler::handle(const ResultNode & arg)
{
    const ResultNodeVector & v(static_cast<const ResultNodeVector &>(arg));
   _result.getVector().resize(v.size());
    for(size_t i(0), m(_result.getVector().size()); i < m; i++) {
        handleOne(v.get(i), _result.getVector()[i]);
    }
}

Serializer & TimeStampFunctionNode::onSerialize(Serializer & os) const
{
    UnaryFunctionNode::onSerialize(os);
    uint8_t code(getTimePart() | (isGmt() ? 0x80 : 0x00));
    return os << code;
}

Deserializer & TimeStampFunctionNode::onDeserialize(Deserializer & is)
{
    UnaryFunctionNode::onDeserialize(is);
    uint8_t code(0);
    is >> code;
    _isGmt = code & 0x80;
    _timePart = static_cast<TimePart>(code & 0x7f);
    return is;
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_timestamp() {}
