// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/searchlib/common/feature.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/nearest_neighbor_iterator.h>
#include <vespa/searchlib/queryeval/simpleresult.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/distance_function_factory.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/searchlib/queryeval/nns_index_iterator.h>

#include <vespa/log/log.h>
LOG_SETUP("nearest_neighbor_test");

#define EPS 1.0e-6

using search::feature_t;
using search::tensor::DenseTensorAttribute;
using search::AttributeVector;
using vespalib::eval::ValueType;
using CellType = vespalib::eval::ValueType::CellType;
using vespalib::eval::TensorSpec;
using vespalib::tensor::Tensor;
using vespalib::tensor::DenseTensorView;
using vespalib::tensor::DefaultTensorEngine;
using search::tensor::DistanceFunction;
using search::attribute::DistanceMetric;

using namespace search::fef;
using namespace search::queryeval;

vespalib::string denseSpecDouble("tensor(x[2])");
vespalib::string denseSpecFloat("tensor<float>(x[2])");

DistanceFunction::UP euclid_d = search::tensor::make_distance_function(DistanceMetric::Euclidean, CellType::DOUBLE);
DistanceFunction::UP euclid_f = search::tensor::make_distance_function(DistanceMetric::Euclidean, CellType::FLOAT);

std::unique_ptr<DenseTensorView> createTensor(const TensorSpec &spec) {
    auto value = DefaultTensorEngine::ref().from_spec(spec);
    DenseTensorView *tensor = dynamic_cast<DenseTensorView*>(value.get());
    ASSERT_TRUE(tensor != nullptr);
    value.release();
    return std::unique_ptr<DenseTensorView>(tensor);
}

std::unique_ptr<DenseTensorView> createTensor(const vespalib::string& type_spec, double v1, double v2) {
    return createTensor(TensorSpec(type_spec).add({{"x", 0}}, v1)
                                             .add({{"x", 1}}, v2));
}

struct Fixture
{
    using BasicType = search::attribute::BasicType;
    using CollectionType = search::attribute::CollectionType;
    using Config = search::attribute::Config;

    Config _cfg;
    vespalib::string _name;
    vespalib::string _typeSpec;
    std::shared_ptr<DenseTensorAttribute> _tensorAttr;
    std::shared_ptr<AttributeVector> _attr;

    Fixture(const vespalib::string &typeSpec)
        : _cfg(BasicType::TENSOR, CollectionType::SINGLE),
          _name("test"),
          _typeSpec(typeSpec),
          _tensorAttr(),
          _attr()
    {
        _cfg.setTensorType(ValueType::from_spec(typeSpec));
        _tensorAttr = makeAttr();
        _attr = _tensorAttr;
        _attr->addReservedDoc();
    }

    ~Fixture() {}

    std::shared_ptr<DenseTensorAttribute> makeAttr() {
        return std::make_shared<DenseTensorAttribute>(_name, _cfg);
    }

    void ensureSpace(uint32_t docId) {
        while (_attr->getNumDocs() <= docId) {
            uint32_t newDocId = 0u;
            _attr->addDoc(newDocId);
            _attr->commit();
        }
    }

    void setTensor(uint32_t docId, const Tensor &tensor) {
        ensureSpace(docId);
        _tensorAttr->setTensor(docId, tensor);
        _attr->commit();
    }

    void setTensor(uint32_t docId, double v1, double v2) {
        auto t = createTensor(_typeSpec, v1, v2);
        setTensor(docId, *t);
    }

    const DistanceFunction *dist_fun() const {
        if (_cfg.tensorType().cell_type() == CellType::FLOAT) {
            return euclid_f.get();
        } else {
            return euclid_d.get();
        }
    }
};

template <bool strict>
SimpleResult find_matches(Fixture &env, const DenseTensorView &qtv) {
    auto md = MatchData::makeTestInstance(2, 2);
    auto &tfmd = *(md->resolveTermField(0));
    auto &attr = *(env._tensorAttr);
    NearestNeighborDistanceHeap dh(2);
    auto search = NearestNeighborIterator::create(strict, tfmd, qtv, attr, dh, env.dist_fun());
    if (strict) {
        return SimpleResult().searchStrict(*search, attr.getNumDocs());
    } else {
        return SimpleResult().search(*search, attr.getNumDocs());
    }
}

void
verify_iterator_returns_expected_results(const vespalib::string& attribute_tensor_type_spec,
                                         const vespalib::string& query_tensor_type_spec)
{
    Fixture fixture(attribute_tensor_type_spec);
    fixture.ensureSpace(6);
    fixture.setTensor(1, 3.0, 4.0);
    fixture.setTensor(2, 6.0, 8.0);
    fixture.setTensor(3, 5.0, 12.0);
    fixture.setTensor(4, 4.0, 3.0);
    fixture.setTensor(5, 8.0, 6.0);
    fixture.setTensor(6, 4.0, 3.0);
    auto nullTensor = createTensor(query_tensor_type_spec, 0.0, 0.0);
    SimpleResult result = find_matches<true>(fixture, *nullTensor);
    SimpleResult nullExpect({1,2,4,6});
    EXPECT_EQUAL(result, nullExpect);
    result = find_matches<false>(fixture, *nullTensor);
    EXPECT_EQUAL(result, nullExpect);
    auto farTensor = createTensor(query_tensor_type_spec, 9.0, 9.0);
    SimpleResult farExpect({1,2,3,5});
    result = find_matches<true>(fixture, *farTensor);
    EXPECT_EQUAL(result, farExpect);
    result = find_matches<false>(fixture, *farTensor);
    EXPECT_EQUAL(result, farExpect);
}

TEST("require that NearestNeighborIterator returns expected results") {
    TEST_DO(verify_iterator_returns_expected_results(denseSpecDouble, denseSpecDouble));
    TEST_DO(verify_iterator_returns_expected_results(denseSpecFloat, denseSpecFloat));
}

template <bool strict>
std::vector<feature_t> get_rawscores(Fixture &env, const DenseTensorView &qtv) {
    auto md = MatchData::makeTestInstance(2, 2);
    auto &tfmd = *(md->resolveTermField(0));
    auto &attr = *(env._tensorAttr);
    NearestNeighborDistanceHeap dh(2);
    auto search = NearestNeighborIterator::create(strict, tfmd, qtv, attr, dh, env.dist_fun());
    uint32_t limit = attr.getNumDocs();
    uint32_t docid = 1;
    search->initRange(docid, limit);
    std::vector<feature_t> rv;
    while (docid < limit) {
        if (search->seek(docid)) {
            search->unpack(docid);
            rv.push_back(tfmd.getRawScore());
        }
        docid = std::max(search->getDocId(), docid + 1);
    }
    return rv;
}

void
verify_iterator_sets_expected_rawscore(const vespalib::string& attribute_tensor_type_spec,
                                       const vespalib::string& query_tensor_type_spec)
{
    Fixture fixture(attribute_tensor_type_spec);
    fixture.ensureSpace(6);
    fixture.setTensor(1, 3.0, 4.0);
    fixture.setTensor(2, 5.0, 12.0);
    fixture.setTensor(3, 6.0, 8.0);
    fixture.setTensor(4, 5.0, 12.0);
    fixture.setTensor(5, 8.0, 6.0);
    fixture.setTensor(6, 4.0, 3.0);
    auto nullTensor = createTensor(query_tensor_type_spec, 0.0, 0.0);
    std::vector<feature_t> got = get_rawscores<true>(fixture, *nullTensor);
    std::vector<feature_t> expected{5.0, 13.0, 10.0, 10.0, 5.0};
    EXPECT_EQUAL(got.size(), expected.size());
    for (size_t i = 0; i < expected.size(); ++i) {
        EXPECT_APPROX(1.0/(1.0+expected[i]), got[i], EPS);
    }
    got = get_rawscores<false>(fixture, *nullTensor);
    EXPECT_EQUAL(got.size(), expected.size());
    for (size_t i = 0; i < expected.size(); ++i) {
        EXPECT_APPROX(1.0/(1.0+expected[i]), got[i], EPS);
    }
}

TEST("require that NearestNeighborIterator sets expected rawscore") {
    TEST_DO(verify_iterator_sets_expected_rawscore(denseSpecDouble, denseSpecDouble));
    TEST_DO(verify_iterator_sets_expected_rawscore(denseSpecFloat, denseSpecFloat));
}

TEST("require that NnsIndexIterator works as expected") {
    std::vector<NnsIndexIterator::Hit> hits{{2,4.0}, {3,9.0}, {5,1.0}, {8,16.0}, {9,36.0}};
    auto md = MatchData::makeTestInstance(2, 2);
    auto &tfmd = *(md->resolveTermField(0));
    auto search = NnsIndexIterator::create(tfmd, hits, euclid_d.get());
    uint32_t docid = 1;
    search->initFullRange();
    bool match = search->seek(docid);
    EXPECT_FALSE(match);
    EXPECT_FALSE(search->isAtEnd());
    EXPECT_EQUAL(2u, search->getDocId());
    docid = 2;
    match = search->seek(docid);
    EXPECT_TRUE(match);
    EXPECT_FALSE(search->isAtEnd());
    EXPECT_EQUAL(docid, search->getDocId());
    search->unpack(docid);
    EXPECT_APPROX(1.0/(1.0+2.0), tfmd.getRawScore(), EPS);

    docid = 3;
    match = search->seek(docid);
    EXPECT_TRUE(match);
    EXPECT_FALSE(search->isAtEnd());
    EXPECT_EQUAL(docid, search->getDocId());
    search->unpack(docid);
    EXPECT_APPROX(1.0/(1.0+3.0), tfmd.getRawScore(), EPS);

    docid = 4;
    match = search->seek(docid);
    EXPECT_FALSE(match);
    EXPECT_FALSE(search->isAtEnd());
    EXPECT_EQUAL(5u, search->getDocId());

    docid = 6;
    match = search->seek(docid);
    EXPECT_FALSE(match);
    EXPECT_FALSE(search->isAtEnd());
    EXPECT_EQUAL(8u, search->getDocId());
    docid = 8;
    search->unpack(docid);
    EXPECT_APPROX(1.0/(1.0+4.0), tfmd.getRawScore(), EPS);
    docid = 9;
    match = search->seek(docid);
    EXPECT_TRUE(match);
    EXPECT_FALSE(search->isAtEnd());
    docid = 10;
    match = search->seek(docid);
    EXPECT_FALSE(match);
    EXPECT_TRUE(search->isAtEnd());

    docid = 4;
    search->initRange(docid, 7);
    match = search->seek(docid);
    EXPECT_FALSE(match);
    EXPECT_FALSE(search->isAtEnd());
    EXPECT_EQUAL(5u, search->getDocId());
    docid = 5;
    search->unpack(docid);
    EXPECT_APPROX(1.0/(1.0+1.0), tfmd.getRawScore(), EPS);
    EXPECT_FALSE(search->isAtEnd());
    docid = 6;
    match = search->seek(docid);
    EXPECT_FALSE(match);
    EXPECT_TRUE(search->isAtEnd());
}

TEST_MAIN() { TEST_RUN_ALL(); }
