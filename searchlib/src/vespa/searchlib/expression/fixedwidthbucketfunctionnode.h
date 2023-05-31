// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "unaryfunctionnode.h"
#include "numericresultnode.h"
#include "integerbucketresultnode.h"
#include "floatbucketresultnode.h"
#include <memory>

namespace search::expression {

class FixedWidthBucketFunctionNode : public UnaryFunctionNode
{
public:
    // update result bucket based on numeric value
    struct BucketHandler {
        using CP = vespalib::CloneablePtr<BucketHandler>;
        virtual void update(ResultNode &result, const ResultNode &value) const = 0;
        virtual BucketHandler *clone() const = 0;
        virtual ~BucketHandler() = default;
    };

    // update integer result bucket based on integer value
    struct IntegerBucketHandler : public BucketHandler {
        int64_t width;
        IntegerBucketHandler(int64_t w) : width(w) {}
        void update(ResultNode &result, const ResultNode &value) const override;
        IntegerBucketHandler *clone() const override { return new IntegerBucketHandler(*this); }
    };
    struct IntegerVectorBucketHandler : public IntegerBucketHandler {
        IntegerVectorBucketHandler(int64_t w) : IntegerBucketHandler(w) { }
        void update(ResultNode &result, const ResultNode &value) const override;
        IntegerVectorBucketHandler *clone() const override { return new IntegerVectorBucketHandler(*this); }
    };

    // update float result bucket based on float value
    struct FloatBucketHandler : public BucketHandler {
        double width;
        FloatBucketHandler(double w) : width(w) {}
        void update(ResultNode &result, const ResultNode &value) const override;
        FloatBucketHandler *clone() const override { return new FloatBucketHandler(*this); }
    };

    struct FloatVectorBucketHandler : public FloatBucketHandler {
        FloatVectorBucketHandler(double w) : FloatBucketHandler(w) { }
        void update(ResultNode &result, const ResultNode &value) const override;
        FloatVectorBucketHandler *clone() const override { return new FloatVectorBucketHandler(*this); }
    };
private:
    void onPrepareResult() override;
    bool onExecute() const override;

    NumericResultNode::CP _width;
    BucketHandler::CP     _bucketHandler;

public:
    DECLARE_EXPRESSIONNODE(FixedWidthBucketFunctionNode);
    DECLARE_NBO_SERIALIZE;
    FixedWidthBucketFunctionNode() : UnaryFunctionNode(), _width(), _bucketHandler() {}
    FixedWidthBucketFunctionNode(ExpressionNode::UP arg) : UnaryFunctionNode(std::move(arg)), _width(), _bucketHandler() {}
    ~FixedWidthBucketFunctionNode() override;
    FixedWidthBucketFunctionNode &setWidth(const NumericResultNode::CP &width) {
        _width = width;
        return *this;
    }
};

}
