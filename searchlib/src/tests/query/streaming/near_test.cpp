// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/common/serialized_query_tree.h>
#include <vespa/searchlib/query/streaming/multi_term.h>
#include <vespa/searchlib/query/streaming/near_query_node.h>
#include <vespa/searchlib/query/streaming/onear_query_node.h>
#include <vespa/searchlib/query/streaming/query_term_data.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/searchlib/queryeval/fake_index.h>
#include <vespa/searchlib/queryeval/test/mock_element_gap_inspector.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>
#include <tuple>

using TestHit = std::tuple<uint32_t, uint32_t, int32_t, uint32_t, uint32_t>;

using search::fef::ElementGap;
using search::streaming::Hit;
using search::streaming::HitList;
using search::query::QueryBuilder;
using search::query::Node;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::queryeval::test::MockElementGapInspector;
using search::streaming::NearQueryNode;
using search::streaming::ONearQueryNode;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

inline namespace near_test {

class TestParam {
    bool _ordered;
public:
    TestParam(bool ordered_in)
        : _ordered(ordered_in)
    {
    }
    bool ordered() const noexcept { return _ordered; }
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << (param.ordered() ? "onear" : "near");
    return os;

}

enum class QueryTweak {
    NORMAL,       // All children of query root are term nodes
    PHRASE,       // Last child of query root is a two term phrase
    EARLY_PHRASE, // Next to last child of query root is a two term phrase
    EQUIV         // Last child of query root is an equiv node
};

class MyQueryNodeResultFactory : public QueryNodeResultFactory {
    MockElementGapInspector _mock_element_gap_inspector;
public:
    MyQueryNodeResultFactory(ElementGap element_gap);
    ~MyQueryNodeResultFactory() override;
    const search::queryeval::IElementGapInspector& get_element_gap_inspector() const noexcept override;
};

MyQueryNodeResultFactory::MyQueryNodeResultFactory(ElementGap element_gap)
    : QueryNodeResultFactory(),
      _mock_element_gap_inspector(element_gap)
{
}

MyQueryNodeResultFactory::~MyQueryNodeResultFactory() = default;

const search::queryeval::IElementGapInspector&
MyQueryNodeResultFactory::get_element_gap_inspector() const noexcept
{
    return _mock_element_gap_inspector;
}

class WrappedQuery {
    std::unique_ptr<MyQueryNodeResultFactory> _factory; // contains element gap inspector
    std::unique_ptr<Query>                    _query;
public:
    WrappedQuery(std::unique_ptr<MyQueryNodeResultFactory> factory_in, std::unique_ptr<Query> query_in);
    ~WrappedQuery();
    Query& query() const noexcept { return *_query; }
};

WrappedQuery::WrappedQuery(std::unique_ptr<MyQueryNodeResultFactory> factory_in, std::unique_ptr<Query> query_in)
    : _factory(std::move(factory_in)),
      _query(std::move(query_in))
{
}

WrappedQuery::~WrappedQuery() = default;

}

class NearTest : public ::testing::TestWithParam<TestParam> {
protected:
    std::optional<ElementGap> _element_gap_setting;
    NearTest();
    ~NearTest() override;
    bool evaluate_query(uint32_t distance, const std::vector<std::vector<TestHit>>& hitsvv);
    bool evaluate_query(QueryTweak query_tweak, uint32_t distance, const std::vector<std::vector<TestHit>>& hitsvv);
    WrappedQuery make_query(QueryTweak query_tweak, uint32_t distance, const std::vector<std::vector<TestHit>>& hitsvv);
    std::vector<uint32_t> get_element_ids(QueryTweak query_tweak, uint32_t distance, const std::vector<std::vector<TestHit>>& hitsvv);

    // Visual test support
    struct NearSpec {
        std::string _terms;
        uint32_t _window;
        std::optional<std::vector<uint32_t>> _field_ids;
        NearTest* _test;

        NearSpec(const std::string& terms, uint32_t window, NearTest* test)
            : _terms(terms), _window(window), _field_ids(std::nullopt), _test(test) {}
        ~NearSpec();

        template <typename... Args>
        NearSpec& fields(Args... field_ids) {
            _field_ids = std::vector<uint32_t>{static_cast<uint32_t>(field_ids)...};
            return *this;
        }

        void verify(const search::queryeval::FakeIndex& index, uint32_t docid,
                   const std::vector<uint32_t>& expected_elements);
    };

    NearSpec near(const std::string& terms, uint32_t window) {
        return NearSpec(terms, window, this);
    }

    search::queryeval::FakeIndex index() { return {}; }
};

NearTest::NearTest()
    : ::testing::TestWithParam<TestParam>(),
      _element_gap_setting()
{
}

NearTest::~NearTest() = default;
NearTest::NearSpec::~NearSpec() = default;

bool
NearTest::evaluate_query(uint32_t distance, const std::vector<std::vector<TestHit>>& hitsvv)
{
    return evaluate_query(QueryTweak::NORMAL, distance, hitsvv);
}

bool
NearTest::evaluate_query(QueryTweak query_tweak, uint32_t distance, const std::vector<std::vector<TestHit>>& hitsvv)
{
    auto wrapped_query = make_query(query_tweak, distance, hitsvv);
    return wrapped_query.query().getRoot().evaluate();
}

WrappedQuery
NearTest::make_query(QueryTweak query_tweak, uint32_t distance, const std::vector<std::vector<TestHit> >& hitsvv)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    auto num_terms = hitsvv.size();
    auto top_arity = num_terms;
    if (query_tweak != QueryTweak::NORMAL) {
        EXPECT_LT(2, num_terms);
        assert(num_terms > 2);
        --top_arity;
    }
    if (GetParam().ordered()) {
        builder.addONear(top_arity, distance, 0, 0);
    } else {
        builder.addNear(top_arity, distance, 0, 0);
    }
    for (uint32_t idx = 0; idx < hitsvv.size(); ++idx) {
        switch (query_tweak) {
            case QueryTweak::PHRASE:
                if (idx == hitsvv.size() - 2) {
                    builder.addPhrase(2, "field", num_terms, Weight(0));
                }
                break;
            case QueryTweak::EARLY_PHRASE:
                if (idx == hitsvv.size() - 3) {
                    builder.addPhrase(2, "field", num_terms, Weight(0));
                }
                break;
            case QueryTweak::EQUIV:
                if (idx == hitsvv.size() - 2) {
                    builder.addEquiv(2, num_terms, Weight(0));
                }
                break;
            default:
                break;
        }
        vespalib::asciistream s;
        s << "s" << idx;
        builder.addStringTerm(s.str(), "field", idx, Weight(0));
    }
    auto node = builder.build();
    auto serializedQueryTree = StackDumpCreator::createSerializedQueryTree(*node);
    auto empty = std::make_unique<MyQueryNodeResultFactory>(_element_gap_setting.value_or(std::nullopt));
    auto q = std::make_unique<Query>(*empty, *serializedQueryTree);
    if (GetParam().ordered()) {
        auto& top = dynamic_cast<ONearQueryNode&>(q->getRoot());
        EXPECT_EQ(top_arity, top.size());
    } else {
        auto& top = dynamic_cast<NearQueryNode&>(q->getRoot());
        EXPECT_EQ(top_arity, top.size());
    }
    QueryTermList visible_terms;
    QueryTermList terms;
    q->getLeaves(visible_terms);
    for (auto visible_term : visible_terms) {
        auto* multi_term = visible_term->as_multi_term();
        if (multi_term != nullptr) {
            auto& hidden_terms = multi_term->get_terms();
            for (auto& hidden_term : hidden_terms) {
                terms.push_back(hidden_term.get());
            }
        } else {
            terms.push_back(visible_term);
        }
    }
    EXPECT_EQ(hitsvv.size(), terms.size());
    for (QueryTerm * qt : terms) {
        qt->resizeFieldId(1);
    }
    for (uint32_t idx = 0; idx < hitsvv.size(); ++idx) {
        auto& hitsv = hitsvv[idx];
        auto& term = terms[idx];
        for (auto& hit : hitsv) {
            auto hl_idx = term->add(std::get<0>(hit), std::get<1>(hit), std::get<2>(hit), std::get<4>(hit));
            term->set_element_length(hl_idx, std::get<3>(hit));
        }
    }
    return WrappedQuery(std::move(empty), std::move(q));
}

std::vector<uint32_t>
NearTest::get_element_ids(QueryTweak query_tweak, uint32_t distance, const std::vector<std::vector<TestHit>>& hitsvv)
{
    auto wrapped_query = make_query(query_tweak, distance, hitsvv);
    std::vector<uint32_t> result;
    wrapped_query.query().getRoot().get_element_ids(result);
    return result;
}

void
NearTest::NearSpec::verify(const search::queryeval::FakeIndex& index, uint32_t docid,
                          const std::vector<uint32_t>& expected_elements)
{
    MockElementGapInspector element_gap_inspector(_test->_element_gap_setting.value_or(std::nullopt));

    // Create Near or ONear node
    std::unique_ptr<search::streaming::QueryNode> root;
    if (_test->GetParam().ordered()) {
        root = std::make_unique<ONearQueryNode>(element_gap_inspector);
    } else {
        root = std::make_unique<NearQueryNode>(element_gap_inspector);
    }
    auto* near_node = static_cast<NearQueryNode*>(root.get());
    near_node->distance(_window);

    // Create term nodes and add hits
    for (char ch : _terms) {
        auto hits = index.get_streaming_hits(ch, docid, _field_ids);

        // Determine max field_id from actual hits
        uint32_t max_field_id = 0;
        for (const auto& hit : hits) {
            max_field_id = std::max(max_field_id, hit.field_id());
        }

        vespalib::asciistream term_str;
        term_str << ch;
        auto term = std::make_unique<QueryTerm>(std::make_unique<search::streaming::QueryTermData>(),
                                                term_str.str(), "field", QueryTerm::Type::WORD);
        term->resizeFieldId(max_field_id);

        for (const auto& hit : hits) {
            auto hl_idx = term->add(hit.field_id(), hit.element_id(),
                                   hit.element_weight(), hit.position());
            term->set_element_length(hl_idx, hit.element_length());
        }

        near_node->addChild(std::move(term));
    }

    // Get actual element IDs
    std::vector<uint32_t> actual_elements;
    root->get_element_ids(actual_elements);

    EXPECT_EQ(expected_elements, actual_elements);
}

TEST_P(NearTest, test_empty_near)
{
    EXPECT_FALSE(evaluate_query(4, { }));
}

TEST_P(NearTest, test_near_success)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 6, 0} },
                                    { { 0, 0, 10, 6, 2} },
                                    { { 0, 0, 10, 6, 4} } }));
}

TEST_P(NearTest, test_near_fail_distance_exceeded_first_term)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 6, 0} },
                                     { { 0, 0, 10, 6, 2} },
                                     { { 0, 0, 10, 6, 5} } }));
}

TEST_P(NearTest, test_near_fail_distance_exceeded_second_term)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 6, 2} },
                                     { { 0, 0, 10, 6, 0} },
                                     { { 0, 0, 10, 6, 5} } }));
}

TEST_P(NearTest, test_near_fail_element)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 6, 0} },
                                     { { 0, 0, 10, 6, 2} },
                                     { { 0, 1, 10, 6, 4} } }));
}

TEST_P(NearTest, test_near_fail_field)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 6, 0} },
                                     { { 0, 0, 10, 6, 2} },
                                     { { 1, 0, 10, 6, 4} } }));
}

TEST_P(NearTest, test_near_success_after_step_first_term)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 6, 0}, { 0, 0, 10, 6, 2} },
                                    { { 0, 0, 10, 6, 3} },
                                    { { 0, 0, 10, 6, 5} } }));
}

TEST_P(NearTest, test_near_success_after_step_second_term)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 6, 2} },
                                    { { 0, 0, 10, 6, 0}, {0, 0, 10, 6, 3} },
                                    { { 0, 0, 10, 6, 5} } }));
}

TEST_P(NearTest, test_near_success_in_second_element)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 6, 0}, { 0, 1, 10, 6, 0} },
                                    { { 0, 0, 10, 6, 2}, { 0, 1, 10, 6, 2} },
                                    { { 0, 0, 10, 6, 5}, { 0, 1, 10, 6, 4} } }));
}

TEST_P(NearTest, test_near_success_in_second_field)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 6, 0}, { 1, 0, 10, 6, 0} },
                                    { { 0, 0, 10, 6, 2}, { 1, 0, 10, 6, 2} },
                                    { { 0, 0, 10, 6, 5}, { 1, 0, 10, 6, 4} } }));
}

TEST_P(NearTest, test_order_might_matter)
{
    EXPECT_EQ(!GetParam().ordered(), evaluate_query(4, { { { 0, 0, 10, 6, 2} },
                                                         { { 0, 0, 10, 6, 0} },
                                                         { { 0, 0, 10, 6, 4} } }));
}

TEST_P(NearTest, test_overlap_might_matter)
{
    EXPECT_EQ(!GetParam().ordered(), evaluate_query(4, { { { 0, 0, 10, 6, 0} },
                                                         { { 0, 0, 10, 6, 0} },
                                                         { { 0, 0, 10, 6, 4} } }));
}

TEST_P(NearTest, element_boundary)
{
    std::vector<std::vector<TestHit>> hitsvv({ { { 0, 0, 10, 5, 0} },
                                               { { 0, 1, 10, 5, 1 } } });
    EXPECT_FALSE(evaluate_query(20, hitsvv));
    _element_gap_setting.emplace(0);
    EXPECT_TRUE(evaluate_query(20, hitsvv));
    _element_gap_setting.emplace(14);
    EXPECT_TRUE(evaluate_query(20, hitsvv));
    _element_gap_setting.emplace(15);
    EXPECT_FALSE(evaluate_query(20, hitsvv));
}

TEST_P(NearTest, phrase_below_near)
{
    std::vector<std::vector<TestHit>> hitsvv({ { { 0, 1, 10, 10, 0 }, { 0, 1, 10, 10, 7} },
                                               { { 0, 1, 10, 10, 4 } },
                                               { { 0, 1, 10, 10, 5 } } });
    EXPECT_FALSE(evaluate_query(QueryTweak::PHRASE, 1, hitsvv));
    // The following should succeed for near but phrase length is not taken into account for now.
    EXPECT_FALSE(evaluate_query(QueryTweak::PHRASE, 2, hitsvv));
    EXPECT_EQ(!GetParam().ordered(), evaluate_query(QueryTweak::PHRASE, 3, hitsvv));
    EXPECT_TRUE(evaluate_query(QueryTweak::PHRASE, 4, hitsvv));
}

TEST_P(NearTest, early_phrase_below_near)
{
    std::vector<std::vector<TestHit>> hitsvv({ { { 0, 1, 10, 10, 4 } },
                                               { { 0, 1, 10, 10, 5 } },
                                               { { 0, 1, 10, 10, 0 }, { 0, 1, 10, 10, 7} } });
    EXPECT_FALSE(evaluate_query(QueryTweak::EARLY_PHRASE, 1, hitsvv));
    // The following should succeed for near and onear but phrase length is not taken into account for now.
    EXPECT_FALSE(evaluate_query(QueryTweak::EARLY_PHRASE, 2, hitsvv));
    EXPECT_TRUE(evaluate_query(QueryTweak::EARLY_PHRASE, 3, hitsvv));
    EXPECT_TRUE(evaluate_query(QueryTweak::EARLY_PHRASE, 4, hitsvv));
}

TEST_P(NearTest, equiv_below_near)
{
    std::vector<std::vector<TestHit>> hitsvv({ { { 0, 1, 10, 10, 0 }, { 0, 1, 10, 10, 7} },
                                               { { 0, 1, 10, 10, 4 } },
                                               { { 0, 1, 10, 10, 5 } } });
    EXPECT_FALSE(evaluate_query(QueryTweak::EQUIV, 1, hitsvv));
    EXPECT_EQ(!GetParam().ordered(), evaluate_query(QueryTweak::EQUIV, 2, hitsvv));
    EXPECT_EQ(!GetParam().ordered(), evaluate_query(QueryTweak::EQUIV, 3, hitsvv));
    EXPECT_TRUE(evaluate_query(QueryTweak::EQUIV, 4, hitsvv));
}

TEST_P(NearTest, get_element_ids)
{
    using IDS = std::vector<uint32_t>;
    std::vector<std::vector<TestHit>> hitsvv({ { { 0, 3, 10, 5, 2 }, { 0, 7, 10, 5, 2} },
                                               { { 0, 3, 10, 5, 4 }, { 0, 7, 10, 5, 0} } });
    EXPECT_EQ((GetParam().ordered() ? IDS{ 3 } : IDS{ 3, 7 }), get_element_ids(QueryTweak::NORMAL, 4, hitsvv));
    std::swap(hitsvv[0], hitsvv[1]);
    EXPECT_EQ((GetParam().ordered() ? IDS{ 7 } : IDS{ 3, 7 }), get_element_ids(QueryTweak::NORMAL, 4, hitsvv));
}

TEST_P(NearTest, basic_visual_test)
{
    auto docs = index().doc(69)
        .elem(1, "..A.B.C..")
        .elem(2, "..A.C.B..")
        .elem(3, "..A.B..C.");

    if (GetParam().ordered()) {
        near("ABC", 4).verify(docs, 69, {1});
    } else {
        near("ABC", 4).verify(docs, 69, {1, 2});
    }
}

TEST_P(NearTest, multi_field_visual_test)
{
    auto docs = index().doc(69)
        .field(0).elem(1, "..A.B.C..")
        .field(1).elem(1, "..A.C.B..");

    if (GetParam().ordered()) {
        near("ABC", 4).fields(0, 1).verify(docs, 69, {1});
        near("ABC", 4).fields(1).verify(docs, 69, {});
    } else {
        near("ABC", 4).fields(0, 1).verify(docs, 69, {1});
        near("ABC", 4).fields(1).verify(docs, 69, {1});
    }
}

auto test_values = ::testing::Values(TestParam(false), TestParam(true));

INSTANTIATE_TEST_SUITE_P(NearTests, NearTest, test_values, testing::PrintToStringParamName());
