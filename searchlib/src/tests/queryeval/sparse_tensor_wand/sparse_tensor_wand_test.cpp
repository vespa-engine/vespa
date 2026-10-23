// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/queryeval/executeinfo.h>
#include <vespa/searchlib/queryeval/sparse_tensor_wand_blueprint.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_fast_value_attribute.h>
#include <vespa/searchlib/tensor/tensor_attribute.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>

using search::AttributeFactory;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::fef::MatchData;
using search::fef::TermFieldMatchData;
using search::queryeval::Blueprint;
using search::queryeval::ExecuteInfo;
using search::queryeval::FieldSpec;
using search::queryeval::MatchingPhase;
using search::queryeval::SparseTensorWandBlueprint;
using search::tensor::DirectTensorAttribute;
using search::tensor::SerializedFastValueAttribute;
using search::tensor::TensorAttribute;
using vespalib::IllegalArgumentException;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace {

constexpr uint32_t docid_limit = 9;

struct Hit {
    uint32_t docid;
    double   score;
    bool operator==(const Hit& rhs) const noexcept { return docid == rhs.docid && score == rhs.score; }
};

std::ostream& operator<<(std::ostream& os, const Hit& hit) {
    return os << "{" << hit.docid << ", " << hit.score << "}";
}

using Hits = std::vector<Hit>;

std::unique_ptr<Value> make_value(const TensorSpec& spec) {
    return vespalib::eval::value_from_spec(spec, FastValueBuilderFactory::get());
}

struct TestParam {
    const char* type_spec;
    bool        fast_search;
};

std::ostream& operator<<(std::ostream& os, const TestParam& param) {
    return os << param.type_spec << (param.fast_search ? " fast-search" : "");
}

class SparseTensorWandTest : public ::testing::TestWithParam<TestParam> {
protected:
    std::string                      _type_spec;
    std::shared_ptr<TensorAttribute> _attr;
    std::unique_ptr<Value>           _query_tensor;

    SparseTensorWandTest() : _type_spec(GetParam().type_spec), _attr(), _query_tensor() {
        Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
        cfg.setTensorType(ValueType::from_spec(_type_spec));
        if (GetParam().fast_search) {
            _attr = std::make_shared<DirectTensorAttribute>("field", cfg);
        } else {
            _attr = std::make_shared<SerializedFastValueAttribute>("field", cfg);
        }
        _attr->addReservedDoc();
        _attr->addDocs(docid_limit - 1);
        // query:          a:1, b:1, c:3
        set_tensor(1, {{"a", 1}, {"b", 2}});  // 3
        set_tensor(2, {{"c", 1}});            // 3
        set_tensor(3, {{"d", 5}});            // 0
        set_tensor(4, {{"a", 4}});            // 4
        set_tensor(5, {{"b", 10}, {"c", 1}}); // 13
        // doc 6 has no tensor                // 0
        set_tensor(7, {{"a", -1}});          // -1
        set_tensor(8, {{"a", 2}, {"b", 1}}); // 3
        _attr->commit();
        _query_tensor = make_value(spec({{"a", 1}, {"b", 1}, {"c", 3}}));
    }
    ~SparseTensorWandTest() override;

    TensorSpec spec(std::vector<std::pair<std::string, double>> cells) const {
        TensorSpec result(_type_spec);
        for (const auto& [label, value] : cells) {
            result.add({{"x", label}}, value);
        }
        return result;
    }

    void set_tensor(uint32_t docid, std::vector<std::pair<std::string, double>> cells) {
        _attr->setTensor(docid, *make_value(spec(std::move(cells))));
    }

    std::unique_ptr<SparseTensorWandBlueprint> make_blueprint(uint32_t target_hits, double score_threshold) {
        SparseTensorWandBlueprint::validate(*_attr, *_query_tensor);
        return std::make_unique<SparseTensorWandBlueprint>(FieldSpec("field", 0, 0), *_attr, *_query_tensor,
                                                           target_hits, score_threshold);
    }

    static Hits find_matches(Blueprint& blueprint, bool strict) {
        blueprint.basic_plan(strict, docid_limit);
        blueprint.fetchPostings(ExecuteInfo::FULL);
        auto                md = MatchData::makeTestInstance(1, 1);
        TermFieldMatchData& tfmd = *md->resolveTermField(0);
        auto                itr = blueprint.createSearch(*md);
        EXPECT_EQ(strict, itr->is_strict() == vespalib::Trinary::True);
        Hits hits;
        itr->initRange(1, docid_limit);
        for (uint32_t docid = 1; docid < docid_limit; ++docid) {
            if (itr->seek(docid)) {
                itr->unpack(docid);
                hits.push_back({docid, tfmd.getRawScore()});
            } else if (strict) {
                docid = itr->getDocId() - 1;
            }
        }
        return hits;
    }
};

SparseTensorWandTest::~SparseTensorWandTest() = default;

TEST_P(SparseTensorWandTest, all_documents_scoring_above_threshold_match_when_heap_is_large) {
    auto bp = make_blueprint(100, 0.0);
    Hits expect = {{1, 3.0}, {2, 3.0}, {4, 4.0}, {5, 13.0}, {8, 3.0}};
    EXPECT_EQ(expect, find_matches(*bp, true));
    bp = make_blueprint(100, 0.0);
    EXPECT_EQ(expect, find_matches(*bp, false));
}

TEST_P(SparseTensorWandTest, initial_score_threshold_is_used) {
    auto bp = make_blueprint(100, 3.5);
    Hits expect = {{4, 4.0}, {5, 13.0}};
    EXPECT_EQ(expect, find_matches(*bp, true));
    bp = make_blueprint(100, -2.0);
    expect = {{1, 3.0}, {2, 3.0}, {3, 0.0}, {4, 4.0}, {5, 13.0}, {6, 0.0}, {7, -1.0}, {8, 3.0}};
    EXPECT_EQ(expect, find_matches(*bp, true));
}

TEST_P(SparseTensorWandTest, score_threshold_is_raised_when_heap_is_full) {
    // after docs 1 and 2 the heap is full with threshold 3; doc 4 raises it to 4, and doc 8 is not a hit
    auto bp = make_blueprint(2, 0.0);
    Hits expect = {{1, 3.0}, {2, 3.0}, {4, 4.0}, {5, 13.0}};
    EXPECT_EQ(expect, find_matches(*bp, true));
    bp = make_blueprint(2, 0.0);
    EXPECT_EQ(expect, find_matches(*bp, false));
}

TEST_P(SparseTensorWandTest, later_matching_phases_use_original_threshold_without_updating_heap) {
    auto bp = make_blueprint(2, 0.0);
    EXPECT_EQ(4u, find_matches(*bp, true).size());
    bp->set_matching_phase(MatchingPhase::SECOND_PHASE);
    Hits expect = {{1, 3.0}, {2, 3.0}, {4, 4.0}, {5, 13.0}, {8, 3.0}};
    EXPECT_EQ(expect, find_matches(*bp, true));
}

TEST_P(SparseTensorWandTest, query_tensor_type_must_match_attribute_type) {
    auto other_dim = make_value(TensorSpec("tensor(y{})").add({{"y", "a"}}, 1));
    EXPECT_THROW(SparseTensorWandBlueprint::validate(*_attr, *other_dim), IllegalArgumentException);
    std::string other_cell_type = (_attr->getTensorType().cell_type() == vespalib::eval::CellType::DOUBLE)
                                      ? "tensor<float>(x{})"
                                      : "tensor(x{})";
    auto        other_cells = make_value(TensorSpec(other_cell_type).add({{"x", "a"}}, 1));
    EXPECT_THROW(SparseTensorWandBlueprint::validate(*_attr, *other_cells), IllegalArgumentException);
    EXPECT_NO_THROW(SparseTensorWandBlueprint::validate(*_attr, *_query_tensor));
}

INSTANTIATE_TEST_SUITE_P(, SparseTensorWandTest,
                         ::testing::Values(TestParam{"tensor(x{})", false}, TestParam{"tensor<float>(x{})", false},
                                           TestParam{"tensor<bfloat16>(x{})", false},
                                           TestParam{"tensor<int8>(x{})", false}, TestParam{"tensor(x{})", true},
                                           TestParam{"tensor<float>(x{})", true},
                                           TestParam{"tensor<bfloat16>(x{})", true},
                                           TestParam{"tensor<int8>(x{})", true}),
                         [](const auto& param_info) {
                             std::string name;
                             for (char c : std::string(param_info.param.type_spec)) {
                                 if (std::isalnum(static_cast<unsigned char>(c))) {
                                     name.push_back(c);
                                 }
                             }
                             return name + (param_info.param.fast_search ? "_fast_search" : "");
                         });

AttributeVector::SP make_attribute(const std::string& type_spec) {
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE);
    cfg.setTensorType(ValueType::from_spec(type_spec));
    return AttributeFactory::createAttribute("field", cfg);
}

void expect_invalid_attribute(const AttributeVector& attr, const std::string& query_type_spec) {
    auto query = make_value(TensorSpec(query_type_spec));
    EXPECT_THROW(SparseTensorWandBlueprint::validate(attr, *query), IllegalArgumentException);
}

TEST(SparseTensorWandValidateTest, attribute_must_be_sparse_tensor_with_one_mapped_dimension) {
    Config int_cfg(BasicType::INT32, CollectionType::SINGLE);
    expect_invalid_attribute(*AttributeFactory::createAttribute("field", int_cfg), "tensor(x{})");
    expect_invalid_attribute(*make_attribute("tensor(x[3])"), "tensor(x[3])");
    expect_invalid_attribute(*make_attribute("tensor(x{},y[3])"), "tensor(x{},y[3])");
    expect_invalid_attribute(*make_attribute("tensor(x{},y{})"), "tensor(x{},y{})");
    auto query = make_value(TensorSpec("tensor(x{})"));
    EXPECT_NO_THROW(SparseTensorWandBlueprint::validate(*make_attribute("tensor(x{})"), *query));
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
