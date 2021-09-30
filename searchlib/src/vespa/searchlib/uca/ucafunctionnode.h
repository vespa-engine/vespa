// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unaryfunctionnode.h>
#include <vespa/searchlib/common/sortspec.h>
#include <vespa/searchlib/expression/stringresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>


namespace search::expression {

class UcaFunctionNode : public UnaryFunctionNode
{
public:
    DECLARE_EXPRESSIONNODE(UcaFunctionNode);
    DECLARE_NBO_SERIALIZE;
    UcaFunctionNode();
    ~UcaFunctionNode() override;
    UcaFunctionNode(ExpressionNode::UP arg, const vespalib::string & locale, const vespalib::string & strength);
    UcaFunctionNode(const UcaFunctionNode & rhs);
    UcaFunctionNode & operator = (const UcaFunctionNode & rhs);
private:
    bool onExecute() const override;
    void onPrepareResult() override;
    class Handler {
    public:
        Handler(const UcaFunctionNode & uca);
        virtual ~Handler() = default;
        virtual void handle(const ResultNode & arg) = 0;
    protected:
        void handleOne(const ResultNode & arg, RawResultNode & result) const;
    private:
        const common::BlobConverter & _converter;
        char                          _backingBuffer[32];
        vespalib::BufferRef           _buffer;
    };
    class SingleValueHandler : public Handler {
    public:
        SingleValueHandler(UcaFunctionNode & uca) : Handler(uca), _result(static_cast<RawResultNode &>(uca.updateResult())) { }
        void handle(const ResultNode & arg) override;
    private:
        RawResultNode & _result;
    };
    class MultiValueHandler : public Handler {
    public:
        MultiValueHandler(UcaFunctionNode & uca) : Handler(uca), _result(static_cast<RawResultNodeVector &>(uca.updateResult())) { }
        void handle(const ResultNode & arg) override;
    private:
        RawResultNodeVector & _result;
    };
    vespalib::string          _locale;
    vespalib::string          _strength;
    common::BlobConverter::SP _collator;
    std::unique_ptr<Handler>    _handler;
};

}
