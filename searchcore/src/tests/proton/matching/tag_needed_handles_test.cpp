// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/matching/handlerecorder.h>
#include <vespa/searchcore/proton/matching/matchdatareservevisitor.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/tag_needed_handles.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <ostream>
#include <type_traits>

using proton::matching::HandleRecorder;
using proton::matching::MatchDataReserveVisitor;
using proton::matching::ProtonNodeTypes;
using proton::matching::ProtonTermData;
using proton::matching::ResolveViewVisitor;
using proton::matching::ViewResolver;
using proton::matching::tag_needed_handles;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::FilterThreshold;
using search::fef::MatchDataDetails;
using search::fef::MatchDataLayout;
using search::fef::test::IndexEnvironment;
using search::query::Intermediate;
using search::query::Node;
using search::query::QueryBuilder;
using search::query::TemplateTermVisitor;
using search::query::Weight;
using CollectionType = FieldInfo::CollectionType;

using HandleSet = std::set<uint32_t>;
using ThresholdVector = std::vector<float>;

namespace search::fef {

std::ostream& operator<<(std::ostream& os, MatchDataDetails details) {
    using T = std::underlying_type_t<MatchDataDetails>;
    os << "{ ";
    bool need_comma = false;
    if ((static_cast<T>(details) & static_cast<T>(MatchDataDetails::Normal)) != 0) {
        os << "Normal";
        need_comma = true;
    }
    if ((static_cast<T>(details) & static_cast<T>(MatchDataDetails::Interleaved)) != 0) {
        if (need_comma) {
            os << ", ";
        }
        os << "Interleaved";
    }
    os << " }";
    return os;
}

}

inline namespace need_normal_features_visitor_test {

const std::string view = "view";
const std::string mixed_view = "mixed_view";
const std::string field1 = "field1";
const std::string field2 = "field2";
const std::string field3 = "field3";
constexpr int term_id = 154;
const std::string foo_term = "foo";
const std::string bar_term = "bar";
const std::string baz_term = "baz";
const Weight string_weight(4);

class ExtractThresholdsVisitor : public TemplateTermVisitor<ExtractThresholdsVisitor, ProtonNodeTypes> {
    ThresholdVector& _thresholds;

    void extract_thresholds(const ProtonTermData& n);
public:
    ExtractThresholdsVisitor(std::vector<float>& thresholds);
    ~ExtractThresholdsVisitor() override;
    template <class TermNode>
    void visitTerm(TermNode& n) { extract_thresholds(n); }
    void visit(ProtonNodeTypes::Equiv& n) override;
    void visit(ProtonNodeTypes::Phrase& n) override;
    void visit(ProtonNodeTypes::SameElement& n) override;
};

ExtractThresholdsVisitor::ExtractThresholdsVisitor(ThresholdVector& thresholds)
    : TemplateTermVisitor<ExtractThresholdsVisitor, ProtonNodeTypes>(),
      _thresholds(thresholds)
{

}

ExtractThresholdsVisitor::~ExtractThresholdsVisitor() = default;

void
ExtractThresholdsVisitor::extract_thresholds(const ProtonTermData& n)
{
    auto num_fields = n.numFields();
    for (uint32_t i = 0; i < num_fields; ++i) {
        _thresholds.emplace_back(n.field(i).fieldSpec().get_filter_threshold().threshold());
    }
}

void
ExtractThresholdsVisitor::visit(ProtonNodeTypes::Equiv& n)
{
    extract_thresholds(n);
    visitChildren(n);
}

void
ExtractThresholdsVisitor::visit(ProtonNodeTypes::Phrase& n)
{
    extract_thresholds(n);
    visitChildren(n);
}

void
ExtractThresholdsVisitor::visit(ProtonNodeTypes::SameElement& n)
{
    extract_thresholds(n);
    visitChildren(n);
}

}

class TagNeededHandlesTest : public ::testing::Test {
    static std::unique_ptr<IndexEnvironment> _index_env;
    static std::unique_ptr<ViewResolver>     _view_resolver;
    std::unique_ptr<MatchDataLayout>         _mdl;
    std::unique_ptr<HandleRecorder>          _handle_recorder;
protected:
    TagNeededHandlesTest();
    ~TagNeededHandlesTest() override;
    static void SetUpTestSuite();
    static void TearDownTestSuite();
    void prepare(Node& query);
    std::set<uint32_t> normal_features_handles();
    ThresholdVector extract_thresholds(Node& query);
};

std::unique_ptr<IndexEnvironment> TagNeededHandlesTest::_index_env;
std::unique_ptr<ViewResolver> TagNeededHandlesTest::_view_resolver;

TagNeededHandlesTest::TagNeededHandlesTest()
    : ::testing::Test(),
      _mdl(),
      _handle_recorder()
{
}

TagNeededHandlesTest::~TagNeededHandlesTest() = default;

void
TagNeededHandlesTest::SetUpTestSuite()
{
    _index_env = std::make_unique<IndexEnvironment>();
    auto& fields = _index_env->getFields();
    fields.emplace_back(FieldType::INDEX, CollectionType::ARRAY, field1, 0);
    fields.back().setFilter(true);
    fields.emplace_back(FieldType::INDEX, CollectionType::ARRAY, field2, 1);
    fields.back().set_filter_threshold(FilterThreshold(0.5));
    fields.emplace_back(FieldType::ATTRIBUTE, CollectionType::ARRAY, field3, 2);
    _view_resolver = std::make_unique<ViewResolver>();
    auto& resolver = *_view_resolver;
    resolver.add(view, field1);
    resolver.add(view, field2);
    resolver.add(mixed_view, field1);
    resolver.add(mixed_view, field3);
}

void
TagNeededHandlesTest::TearDownTestSuite()
{
    _index_env.reset();
    _view_resolver.reset();
}

void
TagNeededHandlesTest::prepare(Node& query)
{
    ResolveViewVisitor resolve_visitor(*_view_resolver, *_index_env);
    query.accept(resolve_visitor);
    _mdl = std::make_unique<MatchDataLayout>();
    MatchDataReserveVisitor reserve_visitor(*_mdl);
    query.accept(reserve_visitor);
    _handle_recorder = std::make_unique<HandleRecorder>();
    tag_needed_handles(query, *_handle_recorder, *_index_env);
}

HandleSet
TagNeededHandlesTest::normal_features_handles()
{
    HandleSet result;
    for (auto kv : _handle_recorder->get_handles()) {
        EXPECT_EQ(MatchDataDetails::Normal, kv.second);
        result.insert(kv.first);
    }
    return result;
}

ThresholdVector
TagNeededHandlesTest::extract_thresholds(Node& query)
{
    ThresholdVector thresholds;
    ExtractThresholdsVisitor visitor(thresholds);
    query.accept(visitor);
    return thresholds;
}

TEST_F(TagNeededHandlesTest, no_unpack_for_and_children)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    query_builder.addOr(term_count);
    query_builder.addStringTerm(foo_term, view, term_id, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 1, string_weight);
    auto root = query_builder.build();
    ASSERT_TRUE(root);
    prepare(*root);
    EXPECT_EQ(HandleSet{}, normal_features_handles());
    EXPECT_EQ((ThresholdVector{0.0, 0.5, 0.0, 0.5}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, hidden_unpack_for_equiv_children)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    query_builder.addEquiv(term_count, term_id, string_weight);
    query_builder.addStringTerm(foo_term, view, term_id + 1, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 2, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ(HandleSet{}, normal_features_handles());
    EXPECT_EQ((ThresholdVector{1.0, 1.0, 0.0, 0.5, 0.0, 0.5}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, unpack_for_near_children)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    constexpr uint32_t distance = 7;
    query_builder.addNear(term_count, distance, 0, 0);
    query_builder.addStringTerm(foo_term, view, term_id, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 1, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ((HandleSet{0, 1, 2, 3}), normal_features_handles());
    EXPECT_EQ((ThresholdVector{1.0, 1.0, 1.0, 1.0}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, partial_unpack_for_near_children)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    constexpr uint32_t distance = 7;
    query_builder.addNear(term_count, distance, 0, 0);
    query_builder.addStringTerm(foo_term, mixed_view, term_id, string_weight);
    query_builder.addStringTerm(bar_term, mixed_view, term_id + 1, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ((HandleSet{0, 2}), normal_features_handles());
    EXPECT_EQ((ThresholdVector{1.0, 1.0, 1.0, 1.0}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, unpack_for_near_children_with_equiv)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t near_term_count = 2;
    constexpr uint32_t equiv_term_count = 2;
    constexpr uint32_t distance = 7;
    query_builder.addNear(near_term_count, distance, 0, 0);
    query_builder.addStringTerm(foo_term, view, term_id, string_weight);
    query_builder.addEquiv(equiv_term_count, term_id + 1, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 2, string_weight);
    query_builder.addStringTerm(baz_term, view, term_id + 3, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ((HandleSet{0, 1, 2, 3}), normal_features_handles());
    EXPECT_EQ((ThresholdVector{1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, unpack_for_onear_children)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    constexpr uint32_t distance = 7;
    query_builder.addONear(term_count, distance, 0, 0);
    query_builder.addStringTerm(foo_term, view, term_id, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 1, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ((HandleSet{0, 1, 2, 3}), normal_features_handles());
    EXPECT_EQ((ThresholdVector{1.0, 1.0, 1.0, 1.0}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, hidden_unpack_for_phrase_children)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    query_builder.addPhrase(term_count, view, term_id, string_weight);
    query_builder.addStringTerm(foo_term, view, term_id + 1, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 2, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ(HandleSet{}, normal_features_handles());
    // view is not resolved for phrase children, SimplePhraseBlueprint::getNextChildField disables filter
    EXPECT_EQ((ThresholdVector{0.0, 0.5}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, unpack_for_same_element_children)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    query_builder.addSameElement(term_count, view, term_id, string_weight);
    query_builder.addStringTerm(foo_term, view, term_id + 1, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 2, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ((HandleSet{0, 1, 2, 3}), normal_features_handles());
    EXPECT_EQ((ThresholdVector{0.0, 0.5, 1.0, 1.0, 1.0, 1.0}), extract_thresholds(*root));
}

TEST_F(TagNeededHandlesTest, unpack_for_same_element_with_phrase_child)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    constexpr uint32_t term_count = 2;
    constexpr uint32_t phrase_term_count = 2;
    query_builder.addSameElement(term_count, view, term_id, string_weight);
    query_builder.addStringTerm(foo_term, view, term_id + 1, string_weight);
    query_builder.addPhrase(phrase_term_count, view, term_id, string_weight);
    query_builder.addStringTerm(bar_term, view, term_id + 2, string_weight);
    query_builder.addStringTerm(baz_term, view, term_id + 3, string_weight);
    auto root = query_builder.build();
    prepare(*root);
    EXPECT_EQ((HandleSet{0, 1, 2, 3}), normal_features_handles());
    EXPECT_EQ((ThresholdVector{0.0, 0.5, 1.0, 1.0, 1.0, 1.0}), extract_thresholds(*root));
}

GTEST_MAIN_RUN_ALL_TESTS()
