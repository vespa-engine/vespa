// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"
#include "resultvector.h"
#include "integerresultnode.h"
#include "floatresultnode.h"
#include "stringresultnode.h"

namespace search {
namespace expression {

class RangeBucketPreDefFunctionNode : public UnaryFunctionNode
{
private:
    void onPrepareResult() override;
    bool onExecute() const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

    class Handler {
    public:
        Handler(const RangeBucketPreDefFunctionNode & rangeNode) : _predef(rangeNode.getBucketList()), _nullResult(rangeNode._nullResult) { }
        virtual ~Handler() { }
        virtual const ResultNode * handle(const ResultNode & arg) = 0;
    protected:
        const ResultNodeVector & _predef;
        const ResultNode * _nullResult;
    };
    class SingleValueHandler : public Handler {
    public:
        SingleValueHandler(const RangeBucketPreDefFunctionNode & rangeNode) :
            Handler(rangeNode)
        { }
        const ResultNode * handle(const ResultNode & arg) override;
    };
    class MultiValueHandler : public Handler {
    public:
        MultiValueHandler(const RangeBucketPreDefFunctionNode & rangeNode) :
            Handler(rangeNode),
            _result(static_cast<ResultNodeVector &>(rangeNode.updateResult()))
        { }
        const ResultNode * handle(const ResultNode & arg) override;
    private:
        ResultNodeVector & _result;
    };


    ResultNodeVector::CP       _predef;
    mutable const ResultNode * _result;
    const ResultNode         * _nullResult;
    std::unique_ptr<Handler>     _handler;
    static IntegerBucketResultNode   _nullIntegerResult;
    static FloatBucketResultNode     _nullFloatResult;
    static StringBucketResultNode    _nullStringResult;
    static RawBucketResultNode       _nullRawResult;

public:
    DECLARE_EXPRESSIONNODE(RangeBucketPreDefFunctionNode);
    DECLARE_NBO_SERIALIZE;
    RangeBucketPreDefFunctionNode() : UnaryFunctionNode(), _predef(), _result(NULL), _nullResult(NULL) {}
    RangeBucketPreDefFunctionNode(ExpressionNode::UP arg) : UnaryFunctionNode(std::move(arg)), _predef(), _result(NULL), _nullResult(NULL) {}
    RangeBucketPreDefFunctionNode(const RangeBucketPreDefFunctionNode & rhs);
    RangeBucketPreDefFunctionNode & operator = (const RangeBucketPreDefFunctionNode & rhs);
    ~RangeBucketPreDefFunctionNode();
    const ResultNode * getResult()   const override { return _result; }
    const ResultNodeVector & getBucketList() const { return *_predef; }
    ResultNodeVector       & getBucketList()       { return *_predef; }
    RangeBucketPreDefFunctionNode & setBucketList(const ResultNodeVector & predef) {
        _predef.reset(static_cast<ResultNodeVector *>(predef.clone()));
        return *this;
    }
};

}
}
