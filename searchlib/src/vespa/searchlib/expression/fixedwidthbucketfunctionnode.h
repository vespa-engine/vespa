// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/expression/unaryfunctionnode.h>
#include <vespa/searchlib/expression/numericresultnode.h>
#include <vespa/searchlib/expression/integerbucketresultnode.h>
#include <vespa/searchlib/expression/floatbucketresultnode.h>
#include <vespa/searchlib/expression/resultvector.h>
#include <memory>

namespace search {
namespace expression {

class FixedWidthBucketFunctionNode : public UnaryFunctionNode
{
public:
    // update result bucket based on numeric value
    struct BucketHandler {
        typedef vespalib::CloneablePtr<BucketHandler> CP;
        virtual void update(ResultNode &result, const ResultNode &value) const = 0;
        virtual BucketHandler *clone() const = 0;
        virtual ~BucketHandler() {}
    };

    // update integer result bucket based on integer value
    struct IntegerBucketHandler : public BucketHandler {
        int64_t width;
        IntegerBucketHandler(int64_t w) : width(w) {}
        virtual void update(ResultNode &result, const ResultNode &value) const;
        virtual IntegerBucketHandler *clone() const { return new IntegerBucketHandler(*this); }
    };
    struct IntegerVectorBucketHandler : public IntegerBucketHandler {
        IntegerVectorBucketHandler(int64_t w) : IntegerBucketHandler(w) { }
        virtual void update(ResultNode &result, const ResultNode &value) const;
        virtual IntegerVectorBucketHandler *clone() const { return new IntegerVectorBucketHandler(*this); }
    };

    // update float result bucket based on float value
    struct FloatBucketHandler : public BucketHandler {
        double width;
        FloatBucketHandler(double w) : width(w) {}
        virtual void update(ResultNode &result, const ResultNode &value) const;
        virtual FloatBucketHandler *clone() const { return new FloatBucketHandler(*this); }
    };

    struct FloatVectorBucketHandler : public FloatBucketHandler {
        FloatVectorBucketHandler(double w) : FloatBucketHandler(w) { }
        virtual void update(ResultNode &result, const ResultNode &value) const;
        virtual FloatVectorBucketHandler *clone() const { return new FloatVectorBucketHandler(*this); }
    };
private:
    virtual void onPrepareResult();
    virtual bool onExecute() const;

    NumericResultNode::CP _width;
    BucketHandler::CP     _bucketHandler;

public:
    DECLARE_EXPRESSIONNODE(FixedWidthBucketFunctionNode);
    DECLARE_NBO_SERIALIZE;
    FixedWidthBucketFunctionNode() : UnaryFunctionNode(), _width(), _bucketHandler() {}
    FixedWidthBucketFunctionNode(ExpressionNode::UP arg) : UnaryFunctionNode(std::move(arg)), _width(), _bucketHandler() {}
    FixedWidthBucketFunctionNode &setWidth(const NumericResultNode::CP &width) {
        _width = width;
        return *this;
    }
};

}
}

