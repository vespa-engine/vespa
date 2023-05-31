// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "fixedwidthbucketfunctionnode.h"
#include "integerresultnode.h"
#include "floatresultnode.h"
#include "integerbucketresultnode.h"
#include "floatbucketresultnode.h"
#include "resultvector.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <stdexcept>
#include <cmath>
#include <limits>

namespace search::expression {

IMPLEMENT_EXPRESSIONNODE(FixedWidthBucketFunctionNode, UnaryFunctionNode);

void
FixedWidthBucketFunctionNode::IntegerBucketHandler::update(ResultNode &result, const ResultNode &value) const
{
    IntegerBucketResultNode &bucket = (IntegerBucketResultNode &)result;
    int64_t n = value.getInteger();
    int64_t from = n;
    int64_t to = n;
    if (width > 0) {
        if (n >= 0) {
            from = (n/width) * width;
            if (from >= (std::numeric_limits<int64_t>::max() - width)) {
                to = std::numeric_limits<int64_t>::max();
            } else {
                to = from + width;
            }
        } else {
            to = ((n+1)/width) * width;
            if (to <= (std::numeric_limits<int64_t>::min() + width)) {
                from = std::numeric_limits<int64_t>::min();
            } else {
                from = to - width;
            }
        }
    }
    bucket.setRange(from, to);
}

void
FixedWidthBucketFunctionNode::IntegerVectorBucketHandler::update(ResultNode &result, const ResultNode &value) const
{
    const IntegerResultNodeVector::Vector & v(static_cast<const IntegerResultNodeVector &>(value).getVector());
    IntegerBucketResultNodeVector::Vector & r(static_cast<IntegerBucketResultNodeVector &>(result).getVector());
    r.resize(v.size());
    for (size_t i(0), m(v.size()); i < m; i++) {
        IntegerBucketHandler::update(r[i], v[i]);
    }
}

void
FixedWidthBucketFunctionNode::FloatVectorBucketHandler::update(ResultNode &result, const ResultNode &value) const
{
    const FloatResultNodeVector::Vector & v(static_cast<const FloatResultNodeVector &>(value).getVector());
    FloatBucketResultNodeVector::Vector & r(static_cast<FloatBucketResultNodeVector &>(result).getVector());
    r.resize(v.size());
    for (size_t i(0), m(v.size()); i < m; i++) {
        FloatBucketHandler::update(r[i], v[i]);
    }
}

void
FixedWidthBucketFunctionNode::FloatBucketHandler::update(ResultNode &result, const ResultNode &value) const
{
    FloatBucketResultNode &bucket = (FloatBucketResultNode &)result;
    double n = value.getFloat();
    double from = n;
    double to = n;
    if (width > 0.0) {
        double tmp = std::floor(n/width);
        from = tmp * width;
        to = (tmp+1) * width;
    }
    bucket.setRange(from, to);
}

FixedWidthBucketFunctionNode::~FixedWidthBucketFunctionNode() = default;

void
FixedWidthBucketFunctionNode::onPrepareResult()
{
    const ExpressionNode &child = getArg();
    const ResultNode     &input = *child.getResult();
    if (input.getClass().inherits(IntegerResultNode::classId)) {
        setResultType(std::make_unique<IntegerBucketResultNode>());
        _bucketHandler.reset(new IntegerBucketHandler(_width->getInteger()));
    } else if (input.getClass().inherits(FloatResultNode::classId)) {
        setResultType(std::make_unique<FloatBucketResultNode>());
        _bucketHandler.reset(new FloatBucketHandler(_width->getFloat()));
    } else if (input.getClass().inherits(IntegerResultNodeVector::classId)) {
        setResultType(std::make_unique<IntegerBucketResultNodeVector>());
        _bucketHandler.reset(new IntegerVectorBucketHandler(_width->getInteger()));
    } else if (input.getClass().inherits(FloatResultNodeVector::classId)) {
        setResultType(std::make_unique<FloatBucketResultNodeVector>());
        _bucketHandler.reset(new FloatVectorBucketHandler(_width->getFloat()));
    } else {
        throw std::runtime_error(vespalib::make_string("cannot create appropriate bucket for type '%s'", input.getClass().name()));
    }
}

bool
FixedWidthBucketFunctionNode::onExecute() const
{
    getArg().execute();
    _bucketHandler->update(updateResult(), *getArg().getResult());
    return true;
}

vespalib::Serializer &
FixedWidthBucketFunctionNode::onSerialize(vespalib::Serializer &os) const
{
    UnaryFunctionNode::onSerialize(os);
    return os << _width;
}

vespalib::Deserializer &
FixedWidthBucketFunctionNode::onDeserialize(vespalib::Deserializer &is)
{
    UnaryFunctionNode::onDeserialize(is);
    return is >> _width;
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_fixedwidthbucketfunctionnode() {}
