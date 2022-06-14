// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"
#include "integerresultnode.h"
#include "resultvector.h"

namespace search::expression {

class TimeStampFunctionNode : public UnaryFunctionNode
{
public:
    enum TimePart { Year=0, Month=1, MonthDay=2, WeekDay=3, Hour=4, Minute=5, Second=6, YearDay=7, IsDST=8 };
    DECLARE_EXPRESSIONNODE(TimeStampFunctionNode);
    DECLARE_NBO_SERIALIZE;
    TimeStampFunctionNode();
    TimeStampFunctionNode(ExpressionNode::UP arg, TimePart timePart, bool gmt=true);
    ~TimeStampFunctionNode();
    TimeStampFunctionNode(const TimeStampFunctionNode & rhs);
    TimeStampFunctionNode & operator = (const TimeStampFunctionNode & rhs);
    unsigned int getTime() const { return getResult()->getInteger(); } // Not valid until after node has been prepared
    TimePart getTimePart() const { return _timePart; }
    TimeStampFunctionNode & setTimePart(TimePart timePart) { _timePart = timePart; return *this; }
    bool isGmt()           const { return _isGmt; }
    bool isLocal()         const { return ! isGmt(); }
protected:
/*
unsigned year(timestamp); [1970 - 2039]
unsigned month(timestamp); [1-12]
unsigned date(timestamp); [1-31]
unsigned weekday(timestamp); [1-7]
unsigned hour(timestamp); [0-23]
unsigned minute(timestamp);[0-59]
unsigned second(timestamp);[0-59]
*/
    bool onExecute() const override;
    void onPrepareResult() override;
private:
    class Handler {
    public:
        Handler(const TimeStampFunctionNode & ts) : _timePart(ts.getTimePart()), _isGmt(ts.isGmt()) { }
        virtual ~Handler() { }
        virtual void handle(const ResultNode & arg) = 0;
    protected:
        void handleOne(const ResultNode & arg, Int64ResultNode & result) const {
            result.set(TimeStampFunctionNode::getTimePart(arg.getInteger(), _timePart, _isGmt));
        }
    private:
        TimePart _timePart;
        bool     _isGmt;
    };
    class SingleValueHandler : public Handler {
    public:
        SingleValueHandler(TimeStampFunctionNode & ts) : Handler(ts), _result(static_cast<Int64ResultNode &>(ts.updateResult())) { }
        void handle(const ResultNode & arg) override;
    private:
        Int64ResultNode & _result;
    };
    class MultiValueHandler : public Handler {
    public:
        MultiValueHandler(TimeStampFunctionNode & ts) : Handler(ts), _result(static_cast<IntegerResultNodeVector &>(ts.updateResult())) { }
        void handle(const ResultNode & arg) override;
    private:
        IntegerResultNodeVector & _result;
    };

    const ResultNode & getTimeStamp() const { return *getArg().getResult(); }
    void init();
    Int64ResultNode & updateIntegerResult() const { return static_cast<Int64ResultNode &>(updateResult()); }
    static unsigned getTimePart(time_t time, TimePart, bool gmt);
    TimePart _timePart;
    bool     _isGmt;
    std::unique_ptr<Handler> _handler;
};

}
