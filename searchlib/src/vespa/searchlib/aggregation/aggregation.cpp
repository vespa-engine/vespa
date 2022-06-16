// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "aggregation.h"
#include "expressioncountaggregationresult.h"
#include <vespa/searchlib/expression/resultvector.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vespalib/objects/visit.hpp>
#include <xxhash.h>

using namespace search::expression;

namespace search::aggregation {

namespace {

bool isReady(const ResultNode *myRes, const ResultNode &ref) {
    return (myRes != 0 && myRes->getClass().id() == ref.getClass().id());
}

template<typename Wanted, typename Fallback>
std::unique_ptr<Wanted>
createAndEnsureWanted(const ResultNode & result) {
    std::unique_ptr<ResultNode> tmp = result.createBaseType();
    if (dynamic_cast<Wanted *>(tmp.get()) != nullptr) {
        return std::unique_ptr<Wanted>(static_cast<Wanted *>(tmp.release()));
    } else {
        return std::make_unique<Fallback>();
    }
}

} // namespace search::aggregation::<unnamed>

using vespalib::Serializer;
using vespalib::Deserializer;

#define IMPLEMENT_ABSTRACT_AGGREGATIONRESULT(cclass, base) IMPLEMENT_IDENTIFIABLE_ABSTRACT_NS2(search, aggregation, cclass, base)
#define IMPLEMENT_AGGREGATIONRESULT(cclass, base) IMPLEMENT_IDENTIFIABLE_NS2(search, aggregation, cclass, base)

IMPLEMENT_ABSTRACT_AGGREGATIONRESULT(AggregationResult, ExpressionNode);
IMPLEMENT_AGGREGATIONRESULT(CountAggregationResult,   AggregationResult);
IMPLEMENT_AGGREGATIONRESULT(SumAggregationResult,     AggregationResult);
IMPLEMENT_AGGREGATIONRESULT(MaxAggregationResult,     AggregationResult);
IMPLEMENT_AGGREGATIONRESULT(MinAggregationResult,     AggregationResult);
IMPLEMENT_AGGREGATIONRESULT(AverageAggregationResult, AggregationResult);
IMPLEMENT_AGGREGATIONRESULT(XorAggregationResult,     AggregationResult);
IMPLEMENT_AGGREGATIONRESULT(ExpressionCountAggregationResult, AggregationResult);
IMPLEMENT_AGGREGATIONRESULT(StandardDeviationAggregationResult, AggregationResult);

AggregationResult::AggregationResult() :
    _expressionTree(std::make_shared<ExpressionTree>()),
    _tag(-1)
{ }

AggregationResult::AggregationResult(const AggregationResult &) = default;
AggregationResult & AggregationResult::operator = (const AggregationResult &) = default;

AggregationResult::~AggregationResult() = default;

void
AggregationResult::aggregate(const document::Document & doc, HitRank rank) {
    bool ok(_expressionTree->execute(doc, rank));
    if (ok) {
        onAggregate(*_expressionTree->getResult(), doc, rank);
    } else {
        throw std::runtime_error(vespalib::make_string("aggregate(%s, %f) failed ", doc.getId().toString().c_str(), rank));
    }
}
void
AggregationResult::aggregate(DocId docId, HitRank rank) {
    bool ok(_expressionTree->execute(docId, rank));
    if (ok) {
        onAggregate(*_expressionTree->getResult(), docId, rank);
    } else {
        throw std::runtime_error(vespalib::make_string("aggregate(%u, %f) failed ", docId, rank));
    }
}

bool
AggregationResult::Configure::check(const vespalib::Identifiable &obj) const
{
    return obj.inherits(AggregationResult::classId);
}

void
AggregationResult::Configure::execute(vespalib::Identifiable &obj)
{
    auto & a(static_cast<AggregationResult &>(obj));
    a.prepare();
}

AggregationResult &
AggregationResult::setExpression(ExpressionNode::UP expr)
{
    _expressionTree = std::make_shared<ExpressionTree>(std::move(expr));
    prepare(_expressionTree->getResult(), false);
    return *this;
}

void
CountAggregationResult::onPrepare(const ResultNode & result, bool useForInit)
{
    (void) result;
    (void) useForInit;
}

void
SumAggregationResult::onPrepare(const ResultNode & result, bool useForInit)
{
    if (isReady(_sum.get(), result)) {
        return;
    }
    _sum = createAndEnsureWanted<NumericResultNode, FloatResultNode>(result);
    if ( useForInit ) {
        _sum->set(result);
    }
}

MinAggregationResult::MinAggregationResult() = default;
MinAggregationResult::MinAggregationResult(const ResultNode::CP &result)
    : AggregationResult()
{
    setResult(result);
}
MinAggregationResult::~MinAggregationResult() = default;

void
MinAggregationResult::onPrepare(const ResultNode & result, bool useForInit)
{
    if (isReady(_min.get(), result)) {
        return;
    }
    _min = createAndEnsureWanted<SingleResultNode, FloatResultNode>(result);
    if ( !useForInit ) {
        _min->setMax();
    } else {
        _min->set(result);
    }
}

MaxAggregationResult::MaxAggregationResult() = default;
MaxAggregationResult::MaxAggregationResult(const SingleResultNode & max)
    : AggregationResult(),
      _max(max)
{ }
MaxAggregationResult::~MaxAggregationResult() = default;

void
MaxAggregationResult::onPrepare(const ResultNode & result, bool useForInit)
{
    if (isReady(_max.get(), result)) {
        return;
    }
    _max = createAndEnsureWanted<SingleResultNode, FloatResultNode>(result);
    if ( !useForInit ) {
        _max->setMin();  ///Should figure out how to set min too for float.
    } else {
        _max->set(result);
    }
}

void
AverageAggregationResult::onPrepare(const ResultNode & result, bool useForInit)
{
    if (isReady(_sum.get(), result)) {
        return;
    }
    _sum = createAndEnsureWanted<NumericResultNode, FloatResultNode>(result);
    if ( useForInit ) {
        _sum->set(result);
    }
}

void
XorAggregationResult::onPrepare(const ResultNode & result, bool useForInit)
{
    (void) result;
    (void) useForInit;
}

void
SumAggregationResult::onMerge(const AggregationResult & b)
{
    _sum->add(*static_cast<const SumAggregationResult &>(b)._sum);
}

void
SumAggregationResult::onAggregate(const ResultNode & result)
{
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector &>(result).flattenSum(*_sum);
    } else {
        _sum->add(result);
    }
}

void
SumAggregationResult::onReset()
{
    _sum.reset(static_cast<NumericResultNode *>(_sum->getClass().create()));
}

void
CountAggregationResult::onMerge(const AggregationResult & b)
{
    _count.add(static_cast<const CountAggregationResult &>(b)._count);
}

void
CountAggregationResult::onAggregate(const ResultNode & result)
{
    if (result.isMultiValue()) {
        _count += static_cast<const ResultNodeVector &>(result).size();
    } else {
        ++_count;
    }
}

void
CountAggregationResult::onReset()
{
    setCount(0);
}

void
MaxAggregationResult::onMerge(const AggregationResult & b)
{
    _max->max(*static_cast<const MaxAggregationResult &>(b)._max);
}

void
MaxAggregationResult::onAggregate(const ResultNode & result)
{
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector &>(result).flattenMax(*_max);
    } else {
        _max->max(result);
    }
}

void
MaxAggregationResult::onReset()
{
    _max.reset(static_cast<SingleResultNode *>(_max->getClass().create()));
    _max->setMin();
}

void
MinAggregationResult::onMerge(const AggregationResult & b)
{
    _min->min(*static_cast<const MinAggregationResult &>(b)._min);
}

void
MinAggregationResult::onAggregate(const ResultNode & result)
{
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector &>(result).flattenMin(*_min);
    } else {
        _min->min(result);
    }
}

void
MinAggregationResult::onReset()
{
    _min.reset(static_cast<SingleResultNode *>(_min->getClass().create()));
    _min->setMax();
}

AverageAggregationResult::~AverageAggregationResult() = default;

void
AverageAggregationResult::onMerge(const AggregationResult & b)
{
    const auto & avg(static_cast<const AverageAggregationResult &>(b));
    _sum->add(*avg._sum);
    _count += avg._count;
}

void
AverageAggregationResult::onAggregate(const ResultNode & result)
{
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector &>(result).flattenSum(*_sum);
        _count += static_cast<const ResultNodeVector &>(result).size();
    } else {
        _sum->add(result);
        _count++;
    }
}

void
AverageAggregationResult::onReset()
{
    _count = 0;
    _sum.reset(static_cast<NumericResultNode *>(_sum->getClass().create()));
}

const NumericResultNode &
AverageAggregationResult::getAverage() const
{
    _averageScratchPad = _sum;
    if ( _count > 0 ) {
        _averageScratchPad->divide(Int64ResultNode(_count));
    } else {
        _averageScratchPad->set(Int64ResultNode(0));
    }
    return *_averageScratchPad;
}

void
XorAggregationResult::onMerge(const AggregationResult & b)
{
    _xor.xorOp(static_cast<const XorAggregationResult &>(b)._xor);
}

void
XorAggregationResult::onAggregate(const ResultNode & result)
{
    if (result.isMultiValue()) {
        for (size_t i(0), m(static_cast<const ResultNodeVector &>(result).size()); i < m; i++) {
            _xor.xorOp(static_cast<const ResultNodeVector &>(result).get(i));
        }
    } else {
        _xor.xorOp(result);
    }
}

void
XorAggregationResult::onReset()
{
    _xor = 0;
}

Serializer &
AggregationResult::onSerialize(Serializer & os) const
{
    return (os << *_expressionTree).put(_tag);
}

Deserializer &
AggregationResult::onDeserialize(Deserializer & is)
{
    _expressionTree = std::make_shared<ExpressionTree>();
    return (is >> *_expressionTree).get(_tag);
}

void
AggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    visit(visitor, "expression", _expressionTree);
}

void
AggregationResult::selectMembers(const vespalib::ObjectPredicate & predicate, vespalib::ObjectOperation & operation)
{
    _expressionTree->select(predicate,operation);
}

Serializer &
CountAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    return _count.serialize(os);
}

Deserializer &
CountAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    return _count.deserialize(is);
}

void
CountAggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "count", _count);
}

Serializer &
SumAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    return os << _sum;
}

Deserializer &
SumAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    return is >> _sum;
}

SumAggregationResult::SumAggregationResult() = default;

SumAggregationResult::SumAggregationResult(NumericResultNode::UP sum)
    : AggregationResult(),
      _sum(sum.release())
{ }
SumAggregationResult::~SumAggregationResult() = default;

void
SumAggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "sum", _sum);
}

Serializer &
MinAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    return os << _min;
}

Deserializer &
MinAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    return is >> _min;
}

void
MinAggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "min", _min);
}

Serializer &
MaxAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    return os << _max;
}

Deserializer &
MaxAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    return is >> _max;
}

void
MaxAggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "max", _max);
}

Serializer &
AverageAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    return os.put(_count) << _sum;
}

Deserializer &
AverageAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    return is.get(_count) >> _sum;
}

void
AverageAggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "count", _count);
    visit(visitor, "sum", _sum);
}

Serializer &
XorAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    return _xor.serialize(os);
}

Deserializer &
XorAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    return _xor.deserialize(is);
}

void
XorAggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "xor", _xor);
}

namespace {
// Calculates the sum of all buckets.
template <int BucketBits, typename HashT>
int
calculateRank(const Sketch<BucketBits, HashT> &sketch) {
    if (sketch.getClassId() == SparseSketch<BucketBits, HashT>::classId) {
        return static_cast<const SparseSketch<BucketBits, HashT>&>(sketch)
            .getSize();
    }
    auto normal = static_cast<const NormalSketch<BucketBits, HashT>&>(sketch);
    int rank = 0;
    for (size_t i = 0; i < sketch.BUCKET_COUNT; ++i) {
        rank += normal.bucket[i];
    }
    return rank;
}
}  // namespace

void
ExpressionCountAggregationResult::onMerge(const AggregationResult &r) {
    const auto & result = Identifiable::cast<const ExpressionCountAggregationResult &>(r);
    _hll.merge(result._hll);
    _rank.set(calculateRank(_hll.getSketch()));
}
void
ExpressionCountAggregationResult::onAggregate(const ResultNode &result) {
    size_t hash = result.hash();
    const unsigned int seed = 42;
    hash = XXH32(&hash, sizeof(hash), seed);
    // The rank is a maintained sum of all buckets. This should give
    // almost the same ordering as the actual estimates.
    _rank += _hll.aggregate(hash);
}
void
ExpressionCountAggregationResult::onReset() {
    _hll = HyperLogLog<PRECISION>();
    _rank.set(0);
}
Serializer &
ExpressionCountAggregationResult::onSerialize(Serializer &os) const {
    AggregationResult::onSerialize(os);
    _hll.serialize(os);
    return os;
}
Deserializer &
ExpressionCountAggregationResult::onDeserialize(Deserializer &is) {
    AggregationResult::onDeserialize(is);
    _hll.deserialize(is);
    _rank.set(calculateRank(_hll.getSketch()));
    return is;
}

ExpressionCountAggregationResult::ExpressionCountAggregationResult() = default;
ExpressionCountAggregationResult::~ExpressionCountAggregationResult() = default;

StandardDeviationAggregationResult::StandardDeviationAggregationResult()
    : AggregationResult(), _count(), _sum(), _sumOfSquared(), _stdDevScratchPad()
{
    _stdDevScratchPad.reset(new expression::FloatResultNode());
}

StandardDeviationAggregationResult::~StandardDeviationAggregationResult() = default;

const NumericResultNode&
StandardDeviationAggregationResult::getStandardDeviation() const noexcept
{
    if (_count == 0) {
        _stdDevScratchPad->set(Int64ResultNode(0));
    } else {
        double variance = (_sumOfSquared.getFloat() - _sum.getFloat() * _sum.getFloat() / _count) / _count;
        double stddev = std::sqrt(variance);
        _stdDevScratchPad->set(FloatResultNode(stddev));
    }
    return *_stdDevScratchPad;
}

void
StandardDeviationAggregationResult::onMerge(const AggregationResult &r) {
    const auto & result = Identifiable::cast<const StandardDeviationAggregationResult &>(r);
    _count += result._count;
    _sum.add(result._sum);
    _sumOfSquared.add(result._sumOfSquared);
}

void
StandardDeviationAggregationResult::onAggregate(const ResultNode &result) {
    if (result.isMultiValue()) {
        static_cast<const ResultNodeVector &>(result).flattenSum(_sum);
        static_cast<const ResultNodeVector &>(result).flattenSumOfSquared(_sumOfSquared);
        _count += static_cast<const ResultNodeVector &>(result).size();
    } else {
        _sum.add(result);
        FloatResultNode squared(result.getFloat());
        squared.multiply(result);
        _sumOfSquared.add(squared);
        _count++;
    }
}

void
StandardDeviationAggregationResult::onReset()
{
    _count = 0;
    _sum.set(0.0);
    _sumOfSquared.set(0.0);
}

Serializer &
StandardDeviationAggregationResult::onSerialize(Serializer & os) const
{
    AggregationResult::onSerialize(os);
    double sum = _sum.getFloat();
    double sumOfSquared = _sumOfSquared.getFloat();
    return os << _count << sum << sumOfSquared;
}

Deserializer &
StandardDeviationAggregationResult::onDeserialize(Deserializer & is)
{
    AggregationResult::onDeserialize(is);
    double sum;
    double sumOfSquared;
    auto& r = is >> _count >> sum >> sumOfSquared;
    _sum.set(sum);
    _sumOfSquared.set(sumOfSquared);
    return r;
}

void
StandardDeviationAggregationResult::visitMembers(vespalib::ObjectVisitor &visitor) const
{
    AggregationResult::visitMembers(visitor);
    visit(visitor, "count", _count);
    visit(visitor, "sum", _sum);
    visit(visitor, "sumOfSquared", _sumOfSquared);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_aggregation_aggregation() {}
