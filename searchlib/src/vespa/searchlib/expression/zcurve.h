// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"
#include "integerresultnode.h"
#include "resultvector.h"

namespace search {
namespace expression {

class ZCurveFunctionNode : public UnaryFunctionNode
{
public:
    enum Dimension {X=0, Y=1};
    DECLARE_EXPRESSIONNODE(ZCurveFunctionNode);
    DECLARE_NBO_SERIALIZE;
    ZCurveFunctionNode() : _dim(X) { }
    ZCurveFunctionNode(ExpressionNode::UP arg, Dimension dim) : UnaryFunctionNode(std::move(arg)), _dim(dim) { }
    ZCurveFunctionNode(const ZCurveFunctionNode & rhs);
    ZCurveFunctionNode & operator = (const ZCurveFunctionNode & rhs);
    ~ZCurveFunctionNode();
    Dimension getDim() const { return _dim; }
private:
    class Handler {
    public:
        Handler(Dimension dim) : _dim(dim) { }
        virtual ~Handler() { }
        virtual void handle(const ResultNode & arg) = 0;
    protected:
        void handleOne(const ResultNode & arg, Int64ResultNode & result) const {
            result.set(getXorY(arg.getInteger()));
        }
    private:
        int32_t  getXorY(uint64_t z) const;
        Dimension _dim;
    };
    class SingleValueHandler : public Handler {
    public:
        SingleValueHandler(ZCurveFunctionNode & ts) : Handler(ts.getDim()), _result(static_cast<Int64ResultNode &>(ts.updateResult())) { }
        void handle(const ResultNode & arg) override;
    private:
        Int64ResultNode & _result;
    };
    class MultiValueHandler : public Handler {
    public:
        MultiValueHandler(ZCurveFunctionNode & ts) : Handler(ts.getDim()), _result(static_cast<IntegerResultNodeVector &>(ts.updateResult())) { }
        void handle(const ResultNode & arg) override;
    private:
        IntegerResultNodeVector & _result;
    };

    bool onExecute() const override;
    void onPrepareResult() override;
    Dimension _dim;
    std::unique_ptr<Handler> _handler;
};

}
}
