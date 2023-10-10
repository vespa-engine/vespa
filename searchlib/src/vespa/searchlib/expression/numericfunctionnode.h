// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "multiargfunctionnode.h"
#include "resultvector.h"

namespace search::expression {

class NumericFunctionNode : public MultiArgFunctionNode
{
public:
    DECLARE_ABSTRACT_EXPRESSIONNODE(NumericFunctionNode);
    NumericFunctionNode();
    NumericFunctionNode(const NumericFunctionNode & rhs);
    NumericFunctionNode & operator = (const NumericFunctionNode & rhs);
    ~NumericFunctionNode();
    void reset() override { _handler.reset(); MultiArgFunctionNode::reset(); }
protected:
    void onPrepare(bool preserveAccurateTypes) override;

    class Handler
    {
    public:
        Handler(const NumericFunctionNode & func) : _function(func) { }
        virtual ~Handler() { }
        virtual void handle(const ResultNode & arg) = 0;
        virtual void handleFirst(const ResultNode & arg) = 0;
    protected:
        const NumericFunctionNode & function() const { return _function; }
    private:
        const NumericFunctionNode & _function;
    };

    template <typename T>
    class VectorHandler : public Handler
    {
    protected:
        VectorHandler(const NumericFunctionNode & func) :
            Handler(func),
            _result(static_cast<T &>(func.updateResult()))
        { }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override;
    private:
        T & _result;
    };

    class VectorIntegerHandler : public VectorHandler<IntegerResultNodeVector>
    {
    private:
        using BaseHandler = VectorHandler<IntegerResultNodeVector>;
    public:
        VectorIntegerHandler(const NumericFunctionNode & func) : BaseHandler(func) { }
    };
    class VectorFloatHandler : public VectorHandler<FloatResultNodeVector>
    {
    private:
        using BaseHandler = VectorHandler<FloatResultNodeVector>;
    public:
        VectorFloatHandler(const NumericFunctionNode & func) : BaseHandler(func) { }
    };
    class VectorStringHandler : public VectorHandler<StringResultNodeVector>
    {
    private:
        using BaseHandler = VectorHandler<StringResultNodeVector>;
    public:
        VectorStringHandler(const NumericFunctionNode & func) : BaseHandler(func) { }
    };
private:
    virtual ResultNode::CP getInitialValue() const = 0;
    virtual ResultNode & flatten(const ResultNodeVector & v, ResultNode & result) const = 0;
    class ScalarIntegerHandler : public Handler
    {
    public:
        ScalarIntegerHandler(const NumericFunctionNode & func) :
            Handler(func),
            _result(static_cast<Int64ResultNode &>(func.updateResult()))
        { }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override { _result.set(arg.getInteger()); }
    protected:
        Int64ResultNode & _result;
    };
    class ScalarFloatHandler : public Handler
    {
    public:
        ScalarFloatHandler(const NumericFunctionNode & func) :
            Handler(func),
            _result(static_cast<FloatResultNode &>(func.updateResult()))
        { }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override { _result.set(arg.getFloat()); }
    protected:
        FloatResultNode & _result;
    };
    class ScalarStringHandler : public Handler
    {
    public:
        ScalarStringHandler(const NumericFunctionNode & func) :
            Handler(func),
            _result(static_cast<StringResultNode &>(func.updateResult()))
        { }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override {
            char buf[32];
            vespalib::ConstBufferRef b = arg.getString(vespalib::BufferRef(buf, sizeof(buf)));
            _result.set(vespalib::stringref(b.c_str(), b.size()));
        }
    protected:
        StringResultNode & _result;
    };
    class ScalarRawHandler : public Handler
    {
    public:
        ScalarRawHandler(const NumericFunctionNode & func) :
            Handler(func),
            _result(static_cast<RawResultNode &>(func.updateResult()))
        { }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override {
            char buf[32];
            vespalib::ConstBufferRef b = arg.getString(vespalib::BufferRef(buf, sizeof(buf)));
            _result.setBuffer(b.data(), b.size());
        }
    protected:
        RawResultNode & _result;
    };
    class FlattenIntegerHandler : public ScalarIntegerHandler
    {
    public:
        FlattenIntegerHandler(const NumericFunctionNode & func) :
            ScalarIntegerHandler(func),
            _initial()
        {
            _initial.set(*func.getInitialValue());
        }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override { handle(arg); }
    private:
        Int64ResultNode _initial;
    };
    class FlattenFloatHandler : public ScalarFloatHandler
    {
    public:
        FlattenFloatHandler(const NumericFunctionNode & func) :
            ScalarFloatHandler(func),
            _initial()
        {
            _initial.set(*func.getInitialValue());
        }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override { handle(arg); }
    private:
        FloatResultNode _initial;
    };
    class FlattenStringHandler : public ScalarStringHandler
    {
    public:
        FlattenStringHandler(const NumericFunctionNode & func) :
            ScalarStringHandler(func),
            _initial()
        {
            _initial.set(*func.getInitialValue());
        }
        void handle(const ResultNode & arg) override;
        void handleFirst(const ResultNode & arg) override { handle(arg); }
    private:
        StringResultNode _initial;
    };

    bool onCalculate(const ExpressionNodeVector & args, ResultNode & result) const override;
    std::unique_ptr<Handler> _handler;
};

}
