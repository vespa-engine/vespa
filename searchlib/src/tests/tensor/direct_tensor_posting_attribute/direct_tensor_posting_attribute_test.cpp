// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/i_docid_with_float_weight_posting_store.h>
#include <vespa/searchlib/tensor/direct_tensor_posting_attribute.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/exceptions.h>

#include <cmath>
#include <filesystem>
#include <limits>

using search::AttributeFactory;
using search::AttributeVector;
using search::attribute::BasicType;
using search::attribute::CollectionType;
using search::attribute::Config;
using search::tensor::DirectTensorPostingAttribute;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

namespace {

const std::string test_dir = "test_data";

Config make_config(const std::string& type_spec) {
    Config cfg(BasicType::TENSOR, CollectionType::SINGLE, true);
    cfg.setTensorType(ValueType::from_spec(type_spec));
    cfg.set_tensor_label_postings(true);
    return cfg;
}

std::unique_ptr<Value> make_value(const TensorSpec& spec) {
    return vespalib::eval::value_from_spec(spec, FastValueBuilderFactory::get());
}

TensorSpec sparse(std::vector<std::pair<std::string, double>> cells) {
    TensorSpec spec("tensor(x{})");
    for (const auto& [label, value] : cells) {
        spec.add({{"x", label}}, value);
    }
    return spec;
}

} // namespace

class DirectTensorPostingAttributeTest : public ::testing::Test {
protected:
    std::string                                   _type_spec;
    std::shared_ptr<AttributeVector>              _attr;
    std::shared_ptr<DirectTensorPostingAttribute> _tensor_attr;

    DirectTensorPostingAttributeTest() : _type_spec("tensor(x{})") {
        std::filesystem::create_directories(test_dir);
        setup(_type_spec);
    }
    ~DirectTensorPostingAttributeTest() override { std::filesystem::remove_all(test_dir); }

    void create(const std::string& type_spec) {
        _type_spec = type_spec;
        _attr = AttributeFactory::createAttribute(test_dir + "/my_attr", make_config(type_spec));
        _tensor_attr = std::dynamic_pointer_cast<DirectTensorPostingAttribute>(_attr);
        ASSERT_TRUE(_tensor_attr);
    }

    void setup(const std::string& type_spec) {
        create(type_spec);
        _attr->addReservedDoc();
    }

    void ensure_space(uint32_t docid) {
        while (_attr->getNumDocs() <= docid) {
            uint32_t new_docid = 0;
            _attr->addDoc(new_docid);
        }
    }

    void set_tensor(uint32_t docid, const TensorSpec& spec) {
        ensure_space(docid);
        _tensor_attr->setTensor(docid, *make_value(spec));
        _attr->commit();
    }

    void two_phase_set_tensor(uint32_t docid, const TensorSpec& spec) {
        ensure_space(docid);
        auto value = make_value(spec);
        auto prepare_result = _tensor_attr->prepare_set_tensor(docid, *value);
        _tensor_attr->complete_set_tensor(docid, *value, std::move(prepare_result));
        _attr->commit();
    }

    void clear_doc(uint32_t docid) {
        _attr->clearDoc(docid);
        _attr->commit();
    }

    std::vector<std::pair<uint32_t, float>> weighted_postings(const std::string& label) const {
        std::vector<std::pair<uint32_t, float>> result;
        const auto*                             store = _tensor_attr->label_posting_store();
        EXPECT_TRUE(store != nullptr);
        auto snapshot = store->get_dictionary_snapshot();
        auto lookup = store->lookup(label, snapshot);
        if (!lookup.posting_idx.valid()) {
            return result;
        }
        EXPECT_TRUE(store->has_btree_iterator(lookup.posting_idx));
        float min = std::numeric_limits<float>::max();
        float max = std::numeric_limits<float>::lowest();
        for (auto it = store->create(lookup.posting_idx); it.valid(); ++it) {
            result.emplace_back(it.getKey(), it.getData());
            min = std::min(min, it.getData());
            max = std::max(max, it.getData());
        }
        EXPECT_EQ(lookup.posting_size, result.size());
        auto range = store->get_weight_range(lookup.posting_idx);
        EXPECT_EQ(min, range.getMin());
        EXPECT_EQ(max, range.getMax());
        return result;
    }

    std::vector<uint32_t> postings(const std::string& label) const {
        std::vector<uint32_t> result;
        for (const auto& [docid, weight] : weighted_postings(label)) {
            result.push_back(docid);
        }
        return result;
    }

    uint32_t num_labels() const { return _tensor_attr->get_label_store().get_num_uniques(); }

    TensorSpec stored(uint32_t docid) const { return TensorSpec::from_value(_tensor_attr->get_tensor_ref(docid)); }

    void save_and_load() {
        ASSERT_TRUE(_attr->save());
        create(_type_spec);
        ASSERT_TRUE(_attr->load());
    }
};

using Docids = std::vector<uint32_t>;
using WeightedDocids = std::vector<std::pair<uint32_t, float>>;

TEST_F(DirectTensorPostingAttributeTest, empty_attribute_has_no_labels) {
    EXPECT_EQ(0u, num_labels());
    EXPECT_EQ(Docids(), postings("a"));
    EXPECT_EQ(Docids(), postings(""));
}

TEST_F(DirectTensorPostingAttributeTest, set_tensor_adds_postings_for_labels) {
    set_tensor(1, sparse({{"a", 1}, {"b", 2}}));
    set_tensor(2, sparse({{"b", 3}, {"c", 4}}));
    set_tensor(3, sparse({{"a", 5}}));
    EXPECT_EQ(3u, num_labels());
    EXPECT_EQ(Docids({1, 3}), postings("a"));
    EXPECT_EQ(Docids({1, 2}), postings("b"));
    EXPECT_EQ(Docids({2}), postings("c"));
    EXPECT_EQ(Docids(), postings("d"));
    EXPECT_EQ(sparse({{"a", 1}, {"b", 2}}), stored(1));
}

TEST_F(DirectTensorPostingAttributeTest, labels_are_matched_cased) {
    set_tensor(1, sparse({{"Foo", 1}}));
    set_tensor(2, sparse({{"foo", 1}}));
    EXPECT_EQ(Docids({1}), postings("Foo"));
    EXPECT_EQ(Docids({2}), postings("foo"));
    EXPECT_EQ(Docids(), postings("FOO"));
}

TEST_F(DirectTensorPostingAttributeTest, replacing_tensor_updates_postings_and_dictionary) {
    set_tensor(1, sparse({{"a", 1}, {"b", 2}}));
    set_tensor(2, sparse({{"b", 3}}));
    set_tensor(1, sparse({{"b", 7}, {"c", 8}}));
    EXPECT_EQ(Docids(), postings("a"));
    EXPECT_EQ(Docids({1, 2}), postings("b"));
    EXPECT_EQ(Docids({1}), postings("c"));
    EXPECT_EQ(2u, num_labels());
    EXPECT_EQ(sparse({{"b", 7}, {"c", 8}}), stored(1));
}

TEST_F(DirectTensorPostingAttributeTest, two_phase_set_tensor_updates_postings) {
    two_phase_set_tensor(1, sparse({{"a", 1}}));
    two_phase_set_tensor(1, sparse({{"b", 1}}));
    EXPECT_EQ(Docids(), postings("a"));
    EXPECT_EQ(Docids({1}), postings("b"));
    EXPECT_EQ(1u, num_labels());
}

TEST_F(DirectTensorPostingAttributeTest, clear_doc_removes_postings) {
    set_tensor(1, sparse({{"a", 1}, {"b", 2}}));
    set_tensor(2, sparse({{"b", 3}}));
    clear_doc(1);
    EXPECT_EQ(Docids(), postings("a"));
    EXPECT_EQ(Docids({2}), postings("b"));
    EXPECT_EQ(1u, num_labels());
    clear_doc(2);
    EXPECT_EQ(0u, num_labels());
}

TEST_F(DirectTensorPostingAttributeTest, empty_label_is_handled) {
    set_tensor(1, sparse({{"", 1}}));
    EXPECT_EQ(Docids({1}), postings(""));
    clear_doc(1);
    EXPECT_EQ(Docids(), postings(""));
    EXPECT_EQ(0u, num_labels());
}

TEST_F(DirectTensorPostingAttributeTest, compact_lid_space_and_shrink_removes_postings) {
    set_tensor(1, sparse({{"a", 1}}));
    set_tensor(2, sparse({{"a", 1}, {"b", 1}}));
    set_tensor(3, sparse({{"b", 1}}));
    _attr->compactLidSpace(2);
    EXPECT_EQ(Docids({1}), postings("a"));
    EXPECT_EQ(Docids(), postings("b"));
    EXPECT_EQ(1u, num_labels());
    _attr->shrinkLidSpace();
    EXPECT_EQ(Docids({1}), postings("a"));
}

TEST_F(DirectTensorPostingAttributeTest, many_documents_per_label) {
    constexpr uint32_t num_docs = 5000;
    for (uint32_t docid = 1; docid < num_docs; ++docid) {
        ensure_space(docid);
        _tensor_attr->setTensor(docid, *make_value(sparse({{"all", 1}, {(docid % 2 == 0) ? "even" : "odd", 1}})));
    }
    _attr->commit();
    EXPECT_EQ(3u, num_labels());
    EXPECT_EQ(num_docs - 1, postings("all").size());
    EXPECT_EQ(num_docs / 2, postings("odd").size());
    for (uint32_t docid = 1; docid < num_docs; ++docid) {
        if (docid % 3 == 0) {
            _attr->clearDoc(docid);
        }
    }
    _attr->commit();
    auto all = postings("all");
    EXPECT_EQ(num_docs - 1 - (num_docs - 1) / 3, all.size());
    EXPECT_TRUE(std::none_of(all.begin(), all.end(), [](uint32_t docid) { return docid % 3 == 0; }));
}

TEST_F(DirectTensorPostingAttributeTest, save_and_load_rebuilds_postings) {
    set_tensor(1, sparse({{"a", 1}, {"b", 2}}));
    set_tensor(2, sparse({{"b", 3}, {"c", 4}}));
    set_tensor(4, sparse({{"a", 5}}));
    save_and_load();
    EXPECT_EQ(3u, num_labels());
    EXPECT_EQ(Docids({1, 4}), postings("a"));
    EXPECT_EQ(Docids({1, 2}), postings("b"));
    EXPECT_EQ(Docids({2}), postings("c"));
    EXPECT_EQ(sparse({{"b", 3}, {"c", 4}}), stored(2));
    // postings are maintained after load
    set_tensor(4, sparse({{"c", 1}}));
    clear_doc(1);
    EXPECT_EQ(Docids(), postings("a"));
    EXPECT_EQ(Docids({2}), postings("b"));
    EXPECT_EQ(Docids({2, 4}), postings("c"));
    EXPECT_EQ(2u, num_labels());
}

TEST_F(DirectTensorPostingAttributeTest, posting_lists_have_cell_values_as_weights) {
    set_tensor(1, sparse({{"a", 1.5}, {"b", -2}}));
    set_tensor(2, sparse({{"a", 0.25}}));
    EXPECT_EQ(WeightedDocids({{1, 1.5f}, {2, 0.25f}}), weighted_postings("a"));
    EXPECT_EQ(WeightedDocids({{1, -2.0f}}), weighted_postings("b"));
    // only cell values changed
    set_tensor(1, sparse({{"a", 7}, {"b", -2}}));
    EXPECT_EQ(WeightedDocids({{1, 7.0f}, {2, 0.25f}}), weighted_postings("a"));
    EXPECT_EQ(WeightedDocids({{1, -2.0f}}), weighted_postings("b"));
    set_tensor(2, sparse({{"a", -3}, {"b", 4}}));
    EXPECT_EQ(WeightedDocids({{1, 7.0f}, {2, -3.0f}}), weighted_postings("a"));
    EXPECT_EQ(WeightedDocids({{1, -2.0f}, {2, 4.0f}}), weighted_postings("b"));
    EXPECT_EQ(2u, num_labels());
}

TEST_F(DirectTensorPostingAttributeTest, nan_cell_value_gives_zero_weight) {
    set_tensor(1, sparse({{"a", std::numeric_limits<double>::quiet_NaN()}, {"b", 2}}));
    EXPECT_EQ(WeightedDocids({{1, 0.0f}}), weighted_postings("a"));
    EXPECT_EQ(WeightedDocids({{1, 2.0f}}), weighted_postings("b"));
}

TEST_F(DirectTensorPostingAttributeTest, double_cell_values_are_rounded_up_to_float_weights) {
    constexpr float inf = std::numeric_limits<float>::infinity();
    set_tensor(1, sparse({{"a", 1.00000001}, {"b", -1.00000001}, {"c", 1e-46}, {"d", -1e-46}, {"e", 1e300}}));
    EXPECT_EQ(WeightedDocids({{1, std::nextafter(1.0f, inf)}}), weighted_postings("a"));
    EXPECT_EQ(WeightedDocids({{1, -1.0f}}), weighted_postings("b"));
    EXPECT_EQ(WeightedDocids({{1, std::numeric_limits<float>::denorm_min()}}), weighted_postings("c"));
    EXPECT_EQ(WeightedDocids({{1, -0.0f}}), weighted_postings("d"));
    EXPECT_EQ(WeightedDocids({{1, inf}}), weighted_postings("e"));
}

TEST_F(DirectTensorPostingAttributeTest, weights_are_converted_from_other_cell_types) {
    setup("tensor<int8>(x{})");
    set_tensor(1, TensorSpec("tensor<int8>(x{})").add({{"x", "a"}}, -5).add({{"x", "b"}}, 3));
    EXPECT_EQ(WeightedDocids({{1, -5.0f}}), weighted_postings("a"));
    EXPECT_EQ(WeightedDocids({{1, 3.0f}}), weighted_postings("b"));
}

TEST_F(DirectTensorPostingAttributeTest, weights_survive_save_and_load) {
    set_tensor(1, sparse({{"a", 1.5}, {"b", -2}}));
    set_tensor(3, sparse({{"a", 0.25}}));
    save_and_load();
    EXPECT_EQ(WeightedDocids({{1, 1.5f}, {3, 0.25f}}), weighted_postings("a"));
    EXPECT_EQ(WeightedDocids({{1, -2.0f}}), weighted_postings("b"));
}

TEST_F(DirectTensorPostingAttributeTest, unsupported_tensor_types_are_rejected) {
    EXPECT_THROW(DirectTensorPostingAttribute("a", make_config("tensor(x{},y{})")),
                 vespalib::IllegalArgumentException);
    EXPECT_THROW(DirectTensorPostingAttribute("a", make_config("tensor(x{},y[2])")),
                 vespalib::IllegalArgumentException);
    EXPECT_THROW(DirectTensorPostingAttribute("a", make_config("tensor(x[2])")), vespalib::IllegalArgumentException);
}

TEST_F(DirectTensorPostingAttributeTest, factory_ignores_flag_without_fast_search) {
    auto cfg = make_config("tensor(x{})");
    cfg.setFastSearch(false);
    auto attr = AttributeFactory::createAttribute("b", cfg);
    EXPECT_FALSE(std::dynamic_pointer_cast<DirectTensorPostingAttribute>(attr));
    EXPECT_EQ(nullptr, attr->asTensorAttribute()->label_posting_store());
}

GTEST_MAIN_RUN_ALL_TESTS()
