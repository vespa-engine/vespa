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
#include <vespa/vespalib/test/insertion_operators.h>

#include <vespa/log/log.h>
LOG_SETUP("nearest_neighbor_test");

using search::feature_t;
using search::tensor::DenseTensorAttribute;
using search::AttributeVector;
using vespalib::eval::ValueType;
using vespalib::eval::TensorSpec;
using vespalib::tensor::Tensor;
using vespalib::tensor::DenseTensorView;
using vespalib::tensor::DefaultTensorEngine;

using namespace search::fef;
using namespace search::queryeval;

vespalib::string denseSpec("tensor(x[2])");

std::unique_ptr<DenseTensorView> createTensor(const TensorSpec &spec) {
    auto value = DefaultTensorEngine::ref().from_spec(spec);
    DenseTensorView *tensor = dynamic_cast<DenseTensorView*>(value.get());
    ASSERT_TRUE(tensor != nullptr);
    value.release();
    return std::unique_ptr<DenseTensorView>(tensor);
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
        auto t = createTensor(TensorSpec(denseSpec)
                              .add({{"x", 0}}, v1)
                              .add({{"x", 1}}, v2));
        setTensor(docId, *t);
    }
};

template <bool strict>
SimpleResult find_matches(Fixture &env) {
    using NNI = NearestNeighborIterator<strict>;
    auto md = MatchData::makeTestInstance(2, 2);
    auto qt = createTensor(TensorSpec(denseSpec));

    auto &tfmd = *(md->resolveTermField(0));
    const DenseTensorView &qtv = *qt;
    auto &attr = *(env._tensorAttr);

    NearestNeighborDistanceHeap dh(2);
    NNI search(tfmd, qtv, attr, dh);
    if (strict) {
        return SimpleResult().searchStrict(search, attr.getNumDocs());
    } else {
        return SimpleResult().search(search, attr.getNumDocs());
    }
}

TEST("require that NearestNeighborIterator returns expected results") {
    Fixture fixture(denseSpec);
    fixture.ensureSpace(6);
    fixture.setTensor(1, 3.0, 4.0);
    fixture.setTensor(2, 6.0, 8.0);
    fixture.setTensor(3, 5.0, 12.0);
    fixture.setTensor(4, 4.0, 3.0);
    fixture.setTensor(5, 8.0, 6.0);
    fixture.setTensor(6, 4.0, 3.0);
    SimpleResult result = find_matches<true>(fixture);
    SimpleResult expect({1,2,4,6});
    EXPECT_EQUAL(result, expect);
    result = find_matches<false>(fixture);
    EXPECT_EQUAL(result, expect);
}

template <bool strict>
std::vector<feature_t> get_rawscores(Fixture &env) {
    using NNI = NearestNeighborIterator<strict>;
    auto md = MatchData::makeTestInstance(2, 2);
    auto qt = createTensor(TensorSpec(denseSpec));
    auto &tfmd = *(md->resolveTermField(0));
    const DenseTensorView &qtv = *qt;
    auto &attr = *(env._tensorAttr);
    NearestNeighborDistanceHeap dh(2);
    NNI search(tfmd, qtv, attr, dh);
    uint32_t limit = attr.getNumDocs();
    uint32_t docid = 1;
    search.initRange(docid, limit);
    std::vector<feature_t> rv;
    while (docid < limit) {
        if (strict) {
            search.seek(docid);
            if (search.isAtEnd()) break;
            docid = search.getDocId();
            search.unpack(docid);
            rv.push_back(tfmd.getRawScore());
        } else {
            if (search.seek(docid)) {
                search.unpack(docid);
                rv.push_back(tfmd.getRawScore());
            }
        }
        ++docid;
    }
    return rv;
}

TEST("require that NearestNeighborIterator sets expected rawscore") {
    Fixture fixture(denseSpec);
    fixture.ensureSpace(6);
    fixture.setTensor(1, 3.0, 4.0);
    fixture.setTensor(2, 5.0, 12.0);
    fixture.setTensor(3, 6.0, 8.0);
    fixture.setTensor(4, 5.0, 12.0);
    fixture.setTensor(5, 8.0, 6.0);
    fixture.setTensor(6, 4.0, 3.0);
    std::vector<feature_t> got = get_rawscores<true>(fixture);
    std::vector<feature_t> expected{5.0, 13.0, 10.0, 10.0, 5.0};
    EXPECT_EQUAL(got, expected);
    got = get_rawscores<false>(fixture);
    EXPECT_EQUAL(got, expected);
}

TEST_MAIN() { TEST_RUN_ALL(); }
