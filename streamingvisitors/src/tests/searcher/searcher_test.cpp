// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/searchlib/query/streaming/fuzzy_term.h>
#include <vespa/searchlib/query/streaming/regexp_term.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vsm/searcher/boolfieldsearcher.h>
#include <vespa/vsm/searcher/fieldsearcher.h>
#include <vespa/vsm/searcher/floatfieldsearcher.h>
#include <vespa/vsm/searcher/futf8strchrfieldsearcher.h>
#include <vespa/vsm/searcher/intfieldsearcher.h>
#include <vespa/vsm/searcher/mock_field_searcher_env.h>
#include <vespa/vsm/searcher/utf8exactstringfieldsearcher.h>
#include <vespa/vsm/searcher/utf8flexiblestringfieldsearcher.h>
#include <vespa/vsm/searcher/utf8substringsearcher.h>
#include <vespa/vsm/searcher/utf8substringsnippetmodifier.h>
#include <vespa/vsm/searcher/utf8suffixstringfieldsearcher.h>
#include <vespa/vsm/searcher/tokenizereader.h>
#include <vespa/vsm/vsm/snippetmodifier.h>
#include <concepts>
#include <charconv>
#include <initializer_list>
#include <ostream>
#include <stdexcept>
#include <utility>

using namespace document;
using search::streaming::HitList;
using search::streaming::QueryNodeResultFactory;
using search::streaming::FuzzyTerm;
using search::streaming::RegexpTerm;
using search::streaming::QueryTerm;
using search::Normalizing;
using Searchmethod = VsmfieldsConfig::Fieldspec::Searchmethod;
using search::streaming::QueryTermList;
using TermType = QueryTerm::Type;
using namespace vsm;

template <typename T>
class Vector : public std::vector<T>
{
public:
    Vector() noexcept : std::vector<T>() {}
    Vector(std::initializer_list<T> init) : std::vector<T>(init) {}
    Vector<T> & add(T v) { this->push_back(v); return *this; }
};

using Hits = Vector<std::pair<uint32_t, uint32_t>>;
using StringList = Vector<std::string> ;
using HitsList = Vector<Hits>;
using BoolList = Vector<bool>;
using LongList = Vector<int64_t>;
using FloatList = Vector<float>;
using QTFieldInfo = QueryTerm::FieldInfo;
using FieldInfoList = Vector<QTFieldInfo>;

HitsList is_hit{{{0, 0}}};
HitsList no_hits{{}};

namespace {

template <std::integral T>
std::string_view maybe_consume_into(std::string_view str, T& val_out) {
    auto [ptr, ec] = std::from_chars(str.data(), str.data() + str.size(), val_out);
    if (ec != std::errc()) {
        return str;
    }
    return str.substr(ptr - str.data());
}

// Parse optional prefix match mode, max edits and prefix lock length from term string.
// Syntax:
//   "term"           -> {2, 0, false, "term"} (default max edits, prefix length and prefix match mode)
//   "{p}term"        -> {2, 0, true, "term"}
//   "{1}term"        -> {1, 0, false, "term"}
//   "{p1}term"       -> {1, 0, true, "term"}
//   "{1,3}term"      -> {1, 3, false, "term"}
//   "{p1,3}term"     -> {1, 3, true, "term"}
// .. and so on
//
// Note: this is not a "proper" parser (it accepts empty numeric values); only for testing!
std::tuple<uint8_t, uint32_t, bool, std::string_view> parse_fuzzy_params(std::string_view term) {
    std::string_view orig_term = term;
    if (term.empty() || term[0] != '{') {
        return {2, 0, false, term};
    }
    term = term.substr(1); // skip '{'
    uint8_t  max_edits = 2;
    uint32_t prefix_length = 0;
    bool prefix_match = false;
    if (!term.empty() && term[0] == 'p') {
        prefix_match = true;
        term = term.substr(1); // skip 'p'
    }
    if (!term.empty() && term[0] == '}') {
        return {2, 0, prefix_match, term.substr(1)};
    }
    term = maybe_consume_into(term, max_edits);
    if (term.empty() || (term[0] != ',' && term[0] != '}')) {
        throw std::invalid_argument("malformed fuzzy params at (or after) max_edits: " +
                                    std::string(term) + " in string " + std::string(orig_term));
    }
    if (term[0] == '}') {
        return {max_edits, prefix_length, prefix_match, term.substr(1)};
    }
    term = maybe_consume_into(term.substr(1), prefix_length);
    if (term.empty() || term[0] != '}') {
        throw std::invalid_argument("malformed fuzzy params at (or after) prefix_length: " +
                                    std::string(term) + " in string " + std::string(orig_term));
    }
    return {max_edits, prefix_length, prefix_match, term.substr(1)};
}

}

class Query
{
private:
    void setupQuery(const StringList & terms, Normalizing normalizing) {
        for (const auto & term : terms) {
            ParsedQueryTerm pqt = parseQueryTerm(term);
            ParsedTerm pt = parseTerm(pqt.second);
            std::string effective_index = pqt.first.empty() ? "index" : pqt.first;
            if (pt.second == TermType::REGEXP) {
                qtv.push_back(std::make_unique<RegexpTerm>(eqnr.create(), pt.first, effective_index, TermType::REGEXP, normalizing));
            } else if (pt.second == TermType::FUZZYTERM) {
                auto [max_edits, prefix_lock_length, prefix_match, actual_term] = parse_fuzzy_params(pt.first);
                qtv.push_back(std::make_unique<FuzzyTerm>(eqnr.create(), std::string_view(actual_term.data(), actual_term.size()),
                                                          effective_index, TermType::FUZZYTERM, normalizing, max_edits,
                                                          prefix_lock_length, prefix_match));
            } else {
                qtv.push_back(std::make_unique<QueryTerm>(eqnr.create(), pt.first, effective_index, pt.second, normalizing));
            }
        }
        for (const auto & i : qtv) {
            qtl.push_back(i.get());
        }
    }
public:
    using ParsedQueryTerm = std::pair<std::string, std::string>;
    using ParsedTerm = std::pair<std::string, TermType>;
    QueryNodeResultFactory   eqnr;
    std::vector<QueryTerm::UP> qtv;
    QueryTermList          qtl;

    explicit Query(const StringList & terms) : Query(terms, Normalizing::LOWERCASE_AND_FOLD) {}
    Query(const StringList & terms, Normalizing normalizing);
    ~Query();
    static ParsedQueryTerm parseQueryTerm(const std::string & queryTerm) {
        size_t i = queryTerm.find(':');
        if (i != std::string::npos) {
            return {queryTerm.substr(0, i), queryTerm.substr(i + 1)};
        }
        return {std::string(), queryTerm};
    }
    static ParsedTerm parseTerm(const std::string & term) {
        if (term[0] == '*' && term[term.size() - 1] == '*') {
            return std::make_pair(term.substr(1, term.size() - 2), TermType::SUBSTRINGTERM);
        } else if (term[0] == '*') {
            return std::make_pair(term.substr(1, term.size() - 1), TermType::SUFFIXTERM);
        } else if (term[0] == '#') { // magic regex enabler
            return std::make_pair(term.substr(1), TermType::REGEXP);
        } else if (term[0] == '%') { // equally magic fuzzy enabler
            return std::make_pair(term.substr(1), TermType::FUZZYTERM);
        } else if (term[term.size() - 1] == '*') {
            return std::make_pair(term.substr(0, term.size() - 1), TermType::PREFIXTERM);
        } else {
            return std::make_pair(term, TermType::WORD);
        }
    }
};

Query::Query(const StringList & terms, Normalizing normalizing) : eqnr(), qtv(), qtl() {
    setupQuery(terms, normalizing);
}
Query::~Query() = default;

struct SnippetModifierSetup
{
    Query                            query;
    UTF8SubstringSnippetModifier::SP searcher;
    test::MockFieldSearcherEnv       env;
    SnippetModifier                  modifier;
    explicit SnippetModifierSetup(const StringList & terms);
    ~SnippetModifierSetup();
};

SnippetModifierSetup::SnippetModifierSetup(const StringList & terms)
    : query(terms),
      searcher(new UTF8SubstringSnippetModifier(0)),
      env(),
      modifier(searcher)
{
    env.prepare(*searcher, query.qtl);
}
SnippetModifierSetup::~SnippetModifierSetup() = default;

// helper functions
ArrayFieldValue getFieldValue(const StringList &fv);
ArrayFieldValue getFieldValue(const LongList &fv);
ArrayFieldValue getFieldValue(const FloatList &fv);

bool assertMatchTermSuffix(const std::string &term, const std::string &word);
void assertSnippetModifier(const StringList &query, const std::string &fv, const std::string &exp);
void assertSnippetModifier(SnippetModifierSetup &setup, const FieldValue &fv, const std::string &exp);
void assertQueryTerms(const SnippetModifierManager &man, FieldIdT fId, const StringList &terms);
std::vector<QueryTerm::UP> performSearch(FieldSearcher &fs, const StringList &query, const FieldValue &fv);
HitsList as_hitlist(std::vector<std::unique_ptr<QueryTerm>> qtv);
HitsList hits_list(const BoolList& bl);
size_t count_words(const std::string& field);
FieldInfoList as_field_info_list(std::vector<std::unique_ptr<QueryTerm>> qtv);

HitsList search_string(StrChrFieldSearcher& fs, const StringList& query, const std::string& field) {
    return as_hitlist(performSearch(fs, query, StringFieldValue(field)));
}

HitsList search_string(StrChrFieldSearcher& fs, const std::string& term, const std::string& field) {
    return search_string(fs, StringList{term}, field);
}

HitsList search_string(StrChrFieldSearcher& fs, const StringList& query, const StringList& field) {
    return as_hitlist(performSearch(fs, query, getFieldValue(field)));
}

HitsList search_string(StrChrFieldSearcher& fs, const std::string& term, const StringList& field) {
    return search_string(fs, StringList{term}, field);
}

HitsList search_int(IntFieldSearcher& fs, const StringList& query, int64_t field) {
    return as_hitlist(performSearch(fs, query, LongFieldValue(field)));
}

HitsList search_int(IntFieldSearcher & fs, const std::string& term, int64_t field) {
    return search_int(fs, StringList{term}, field);
}

HitsList search_int(IntFieldSearcher& fs, const StringList& query, const LongList& field) {
    return as_hitlist(performSearch(fs, query, getFieldValue(field)));
}

HitsList search_int(IntFieldSearcher & fs, const std::string& term, const LongList& field) {
    return search_int(fs, StringList{term}, field);
}

HitsList search_bool(BoolFieldSearcher& fs, const StringList& query, bool field) {
    return as_hitlist(performSearch(fs, query, BoolFieldValue(field)));
}

HitsList search_bool(BoolFieldSearcher& fs, const std::string& term, bool field) {
    return search_bool(fs, StringList{term}, field);
}

HitsList search_float(FloatFieldSearcher& fs, const StringList& query, float field) {
    return as_hitlist(performSearch(fs, query, FloatFieldValue(field)));
}

HitsList search_float(FloatFieldSearcher& fs, const std::string& term, float field) {
    return search_float(fs, StringList{term}, field);
}

HitsList search_float(FloatFieldSearcher& fs, const StringList& query, const FloatList& field) {
    return as_hitlist(performSearch(fs, query, getFieldValue(field)));
}

HitsList search_float(FloatFieldSearcher& fs, const std::string& term, const FloatList&field) {
    return search_float(fs, StringList{term}, field);
}

FieldInfoList search_string_field_info(StrChrFieldSearcher& fs, const StringList& query, const std::string& fv) {
    return as_field_info_list(performSearch(fs, query, StringFieldValue(fv)));
}

FieldInfoList search_string_field_info(StrChrFieldSearcher& fs, const StringList& query, const StringList& fv) {
    return as_field_info_list(performSearch(fs, query, getFieldValue(fv)));
}

FieldInfoList search_string_field_info(StrChrFieldSearcher& fs, const std::string& term, const StringList& fv) {
    return search_string_field_info(fs, StringList{term}, fv);
}

FieldInfoList search_string_field_info(StrChrFieldSearcher& fs, const std::string& term, const std::string &fv) {
    return search_string_field_info(fs, StringList{term}, fv);
}

FieldInfoList search_int_field_info(IntFieldSearcher& fs, const StringList& query, int64_t fv) {
    return as_field_info_list(performSearch(fs, query, LongFieldValue(fv)));
}

FieldInfoList search_int_field_info(IntFieldSearcher& fs, const StringList& query, const LongList& fv) {
    return as_field_info_list(performSearch(fs, query, getFieldValue(fv)));
}

FieldInfoList search_int_field_info(IntFieldSearcher& fs, const std::string& term, int64_t fv) {
    return search_int_field_info(fs, StringList{term}, fv);
}

FieldInfoList search_int_field_info(IntFieldSearcher& fs, const std::string& term, const LongList& fv) {
    return search_int_field_info(fs, StringList{term}, fv);
}

FieldInfoList search_float_field_info(FloatFieldSearcher& fs, const StringList& query, float fv) {
    return as_field_info_list(performSearch(fs, query, FloatFieldValue(fv)));
}

FieldInfoList search_float_field_info(FloatFieldSearcher& fs, const StringList& query, const FloatList&fv) {
    return as_field_info_list(performSearch(fs, query, getFieldValue(fv)));
}

FieldInfoList search_float_field_info(FloatFieldSearcher& fs, const std::string& term, float fv) {
    return search_float_field_info(fs, StringList{term}, fv);
}

FieldInfoList search_float_field_info(FloatFieldSearcher& fs, const std::string& term, const FloatList& fv) {
    return search_float_field_info(fs, StringList{term}, fv);
}

/** snippet modifer searcher **/
void assertSnippetModifier(const std::string &term, const std::string &fv, const std::string &exp) {
    assertSnippetModifier(StringList().add(term), fv, exp);
}


ArrayFieldValue
getFieldValue(const StringList & fv)
{

    static ArrayDataType type(*DataType::STRING);
    ArrayFieldValue afv(type);
    for (const auto & v : fv) {
        afv.add(StringFieldValue(v));
    }
    return afv;
}

ArrayFieldValue
getFieldValue(const LongList & fv)
{
    static ArrayDataType type(*DataType::LONG);
    ArrayFieldValue afv(type);
    for (long v : fv) {
        afv.add(LongFieldValue(v));
    }
    return afv;
}

ArrayFieldValue
getFieldValue(const FloatList & fv)
{
    static ArrayDataType type(*DataType::FLOAT);
    ArrayFieldValue afv(type);
    for (float v : fv) {
        afv.add(FloatFieldValue(v));
    }
    return afv;
}

bool
assertMatchTermSuffix(const std::string & term, const std::string & word)
{
    QueryNodeResultFactory eqnr;
    QueryTerm qa(eqnr.create(), term, "index", TermType::WORD, Normalizing::LOWERCASE_AND_FOLD);
    QueryTerm qb(eqnr.create(), word, "index", TermType::WORD, Normalizing::LOWERCASE_AND_FOLD);
    const ucs4_t * a;
    size_t alen = qa.term(a);
    const ucs4_t * b;
    size_t blen = qb.term(b);
    return UTF8StringFieldSearcherBase::matchTermSuffix(a, alen, b, blen);
}

HitsList hits_list(const BoolList& bl) {
    HitsList hl;
    for (bool v : bl) {
        hl.push_back(v ? Hits().add({0, 0}) : Hits());
    }
    return hl;
}

std::vector<QueryTerm::UP>
performSearch(FieldSearcher & fs, const StringList & query, const FieldValue & fv)
{
    Query q(query, fs.normalize_mode());

    // prepare field searcher
    test::MockFieldSearcherEnv env;
    env.prepare(fs, q.qtl);

    // setup document
    SharedFieldPathMap sfim(new FieldPathMapT());
    sfim->emplace_back();
    StorageDocument doc(std::make_unique<document::Document>(), sfim, 1);
    doc.setField(0, document::FieldValue::UP(fv.clone()));

    fs.search(doc);
    return std::move(q.qtv);
}

HitsList as_hitlist(std::vector<std::unique_ptr<QueryTerm>> qtv) {
    HitsList result;
    result.reserve(qtv.size());
    for (auto& qt : qtv) {
        result.emplace_back();
        auto& hits = result.back();
        auto  hl = qt->getHitList();
        hits.reserve(hl.size());
        for (const auto& h : hl) {
            hits.emplace_back(h.element_id(), h.position());
        }
    }
    return result;
}

FieldInfoList as_field_info_list(std::vector<std::unique_ptr<QueryTerm>> qtv) {
    FieldInfoList result;
    result.reserve(qtv.size());
    for (auto& qt : qtv) {
        result.emplace_back(qt->getFieldInfo(0));
    }
    return result;
}

namespace search::streaming {

bool operator==(const QTFieldInfo& lhs, const QTFieldInfo& rhs) {
    return lhs.getHitOffset() == rhs.getHitOffset() &&
           lhs.getHitCount() == rhs.getHitCount() &&
           lhs.getFieldLength() == rhs.getFieldLength();
}

std::ostream& operator<<(std::ostream& os, const QTFieldInfo& fi) {
    os << "{hitoffset=" << fi.getHitOffset() << ",hitcnt=" << fi.getHitCount() << ",flen=" << fi.getFieldLength() << "}";
    return os;
}

}

void
assertSnippetModifier(const StringList & query, const std::string & fv, const std::string & exp)
{
    UTF8SubstringSnippetModifier mod(0);
    performSearch(mod, query, StringFieldValue(fv));
    EXPECT_EQ(mod.getModifiedBuf().getPos(), exp.size());
    std::string actual(mod.getModifiedBuf().getBuffer(), mod.getModifiedBuf().getPos());
    EXPECT_EQ(actual.size(), exp.size());
    EXPECT_EQ(actual, exp);
}

void assertSnippetModifier(SnippetModifierSetup & setup, const FieldValue & fv, const std::string & exp)
{
    FieldValue::UP mfv = setup.modifier.modify(fv);
    const auto & lfv = static_cast<const document::LiteralFieldValueB &>(*mfv.get());
    const std::string & actual = lfv.getValue();
    EXPECT_EQ(actual.size(), exp.size());
    EXPECT_EQ(actual, exp);
}

void assertQueryTerms(const SnippetModifierManager & man, FieldIdT fId, const StringList & terms)
{
    if (terms.empty()) {
        ASSERT_TRUE(man.getModifiers().getModifier(fId) == nullptr);
        return;
    }
    ASSERT_TRUE(man.getModifiers().getModifier(fId) != nullptr);
    UTF8SubstringSnippetModifier * searcher =
        (static_cast<SnippetModifier *>(man.getModifiers().getModifier(fId)))->getSearcher().get();
    EXPECT_EQ(searcher->getQueryTerms().size(), terms.size());
    ASSERT_TRUE(searcher->getQueryTerms().size() == terms.size());
    for (size_t i = 0; i < terms.size(); ++i) {
        EXPECT_EQ(std::string(searcher->getQueryTerms()[i]->getTerm()), terms[i]);
    }
}

size_t count_words(const std::string& field)
{
    FieldRef ref(field.c_str(), field.size());
    return FieldSearcher::countWords(ref);
}

void
testStringFieldInfo(StrChrFieldSearcher & fs) {
    EXPECT_EQ(HitsList({{{0, 0}, {1, 0}, {2, 1}}}), search_string(fs, "foo", {"foo bar baz", "foo bar", "baz foo"}));
    EXPECT_EQ(HitsList({{{0, 0}, {1, 0}, {2, 1}}, {{0, 1}, {1, 1}}}),
              search_string(fs, StringList{"foo", "bar"}, {"foo bar baz", "foo bar", "baz foo"}));

    EXPECT_EQ(FieldInfoList({{0, 1, 1}}), search_string_field_info(fs, "foo", "foo"));
    EXPECT_EQ(FieldInfoList({{0, 0, 1}}), search_string_field_info(fs, "bar", "foo"));
    EXPECT_EQ(FieldInfoList({{0, 1, 3}}), search_string_field_info(fs, "foo", "foo bar baz"));
    EXPECT_EQ(FieldInfoList({{0, 1, 3}}), search_string_field_info(fs, "bar", "foo bar baz"));
    EXPECT_EQ(FieldInfoList({{0, 1, 3}}), search_string_field_info(fs, "baz", "foo bar baz"));
    EXPECT_EQ(FieldInfoList({{0, 0, 3}}), search_string_field_info(fs, "qux", "foo bar baz"));
    EXPECT_EQ(FieldInfoList({{0, 3, 3}}), search_string_field_info(fs, "foo", "foo foo foo"));
    // query term size > last term size
    EXPECT_EQ(FieldInfoList({{0, 1, 3}}), search_string_field_info(fs, "runner", "Road Runner Disco"));
    EXPECT_EQ(FieldInfoList({{0, 0, 3}, {0, 1, 3}}),
              search_string_field_info(fs, StringList{"roadrun", "runner"}, "Road Runner Disco"));
    // multiple terms
    EXPECT_EQ(FieldInfoList({{0, 2, 5}}), search_string_field_info(fs, "foo", StringList{"foo bar baz", "foo bar"}));
    EXPECT_EQ(FieldInfoList({{0, 1, 3}, {0, 1, 3}}),
              search_string_field_info(fs, StringList{"foo", "baz"}, "foo bar baz"));
    EXPECT_EQ(FieldInfoList({{0, 2, 5}, {0, 1, 5}}),
              search_string_field_info(fs, StringList{"foo", "baz"}, StringList{"foo bar baz", "foo bar"}));
}

void
testStrChrFieldSearcher(StrChrFieldSearcher & fs)
{
    std::string field = "operators and operator overloading with utf8 char oe = \xc3\x98";
    EXPECT_EQ(HitsList({{}}), search_string(fs, "oper", field));
    EXPECT_EQ(HitsList({{}}), search_string(fs, "tor", field));
    EXPECT_EQ(HitsList({{{0, 0}, {0, 2}}}), search_string(fs, "oper*", field));
    EXPECT_EQ(HitsList({{{0, 1}}}), search_string(fs, "and", field));

    EXPECT_EQ(HitsList({{}, {}}), search_string(fs, StringList{"oper", "tor"}, field));
    EXPECT_EQ(HitsList({{{0, 1}},  {{0, 3}}}), search_string(fs, StringList{"and", "overloading"}, field));

    fs.match_type(FieldSearcher::PREFIX);
    EXPECT_EQ(HitsList({{{0, 0}, {0, 2}}}), search_string(fs, "oper",  field));
    EXPECT_EQ(HitsList({{{0, 0}, {0, 2}}, {}}), search_string(fs, StringList{"oper", "tor"}, field));

    fs.match_type(FieldSearcher::REGULAR);
    testStringFieldInfo(fs);

    { // test handling of several underscores
        StringList query{"foo", "bar"};
        HitsList exp{{{0, 0}}, {{0, 1}}};
        EXPECT_EQ(exp, search_string(fs, query, "foo_bar"));
        EXPECT_EQ(exp, search_string(fs, query, "foo__bar"));
        EXPECT_EQ(exp, search_string(fs, query, "foo___bar"));
        EXPECT_EQ(exp, search_string(fs, query, "foo________bar"));
        EXPECT_EQ(exp, search_string(fs, query, "foo____________________bar"));
        EXPECT_EQ(exp, search_string(fs, query, "________________________________________foo________________________________________bar________________________________________"));
        query = StringList{"foo", "thisisaveryveryverylongword"};
        EXPECT_EQ(exp, search_string(fs, query, "foo____________________thisisaveryveryverylongword"));

        EXPECT_EQ(HitsList({{{0, 1}}}), search_string(fs, "bar", "foo                    bar"));
        EXPECT_EQ(HitsList({{{0, 1}}}), search_string(fs, "bar", "foo____________________bar"));
        EXPECT_EQ(HitsList({{{0, 2}}}), search_string(fs, "bar", "foo____________________thisisaveryveryverylongword____________________bar"));
    }
}

void check_fuzzy_param_parsing(std::string_view term, std::string_view exp_term,
                               uint8_t exp_max_edits, uint32_t exp_prefix_length, bool exp_prefix)
{
    uint8_t max_edits = 0;
    uint32_t prefix_length = 0;
    bool prefix = false;
    std::string_view out;

    std::tie(max_edits, prefix_length, prefix, out) = parse_fuzzy_params(term);
    EXPECT_EQ(static_cast<uint32_t>(max_edits), static_cast<uint32_t>(exp_max_edits)); // don't print as char...
    EXPECT_EQ(prefix_length, exp_prefix_length);
    EXPECT_EQ(prefix, exp_prefix);
    EXPECT_EQ(out, exp_term);

}

TEST(SearcherTest, parsing_of_test_only_fuzzy_term_params_can_extract_expected_values) {
    check_fuzzy_param_parsing("myterm",        "myterm", 2, 0,  false);
    check_fuzzy_param_parsing("{3}myterm",     "myterm", 3, 0,  false);
    check_fuzzy_param_parsing("{p}myterm",     "myterm", 2, 0,  true);
    check_fuzzy_param_parsing("{p1}myterm",    "myterm", 1, 0,  true);
    check_fuzzy_param_parsing("{2,70}myterm",  "myterm", 2, 70, false);
    check_fuzzy_param_parsing("{p2,70}myterm", "myterm", 2, 70, true);
}

TEST(SearcherTest, verify_correct_term_parsing) {
    ASSERT_TRUE(Query::parseQueryTerm("index:term").first == "index");
    ASSERT_TRUE(Query::parseQueryTerm("index:term").second == "term");
    ASSERT_TRUE(Query::parseQueryTerm("term").first.empty());
    ASSERT_TRUE(Query::parseQueryTerm("term").second == "term");
    ASSERT_TRUE(Query::parseTerm("*substr*").first == "substr");
    ASSERT_TRUE(Query::parseTerm("*substr*").second == TermType::SUBSTRINGTERM);
    ASSERT_TRUE(Query::parseTerm("*suffix").first == "suffix");
    ASSERT_TRUE(Query::parseTerm("*suffix").second == TermType::SUFFIXTERM);
    ASSERT_TRUE(Query::parseTerm("prefix*").first == "prefix");
    ASSERT_TRUE(Query::parseTerm("prefix*").second == TermType::PREFIXTERM);
    ASSERT_TRUE(Query::parseTerm("#regex").first == "regex");
    ASSERT_TRUE(Query::parseTerm("#regex").second == TermType::REGEXP);
    ASSERT_TRUE(Query::parseTerm("%fuzzy").first == "fuzzy");
    ASSERT_TRUE(Query::parseTerm("%fuzzy").second == TermType::FUZZYTERM);
    ASSERT_TRUE(Query::parseTerm("term").first == "term");
    ASSERT_TRUE(Query::parseTerm("term").second == TermType::WORD);
}

TEST(SearcherTest, suffix_matching) {
    EXPECT_EQ(assertMatchTermSuffix("a",      "vespa"), true);
    EXPECT_EQ(assertMatchTermSuffix("spa",    "vespa"), true);
    EXPECT_EQ(assertMatchTermSuffix("vespa",  "vespa"), true);
    EXPECT_EQ(assertMatchTermSuffix("vvespa", "vespa"), false);
    EXPECT_EQ(assertMatchTermSuffix("fspa",   "vespa"), false);
    EXPECT_EQ(assertMatchTermSuffix("v",      "vespa"), false);
}

TEST(SearcherTest, Test_basic_strchrfield_searchers) {
    {
        UTF8StrChrFieldSearcher fs(0);
        SCOPED_TRACE("UTF8StrChrFieldSearcher");
        testStrChrFieldSearcher(fs);
    }
    {
        FUTF8StrChrFieldSearcher fs(0);
        SCOPED_TRACE("FUTF8StrChrFieldSearcher");
        testStrChrFieldSearcher(fs);
    }
}

void
testUTF8SubStringFieldSearcher(StrChrFieldSearcher & fs)
{
    std::string field = "operators and operator overloading";
    EXPECT_EQ(HitsList({{}}), search_string(fs, "rsand", field));
    EXPECT_EQ(HitsList({{{0, 3}}}), search_string(fs, "ove", field));
    EXPECT_EQ(HitsList({{{0, 3}}}), search_string(fs, "ing", field));
    EXPECT_EQ(HitsList({{{0, 0}, {0, 2}}}), search_string(fs, "era",   field));
    EXPECT_EQ(HitsList({{{0, 0}, {0, 1}, {0, 2}, {0, 3}}}), search_string(fs, "a", field));

    EXPECT_EQ(HitsList({{},{}}), search_string(fs, StringList{"dn","gn"}, field));
    EXPECT_EQ(HitsList({{{0, 0}, {0, 2}}, {{0, 3}}}), search_string(fs, StringList{"ato", "load"}, field));

    EXPECT_EQ(HitsList({{{0, 0}, {0, 0}, {0, 0}},{{0, 0}}}), search_string(fs, StringList{"aa", "ab"}, "aaaab"));

    testStringFieldInfo(fs);
}

TEST(SearcherTest, utf8_substring_search) {
    {
        UTF8SubStringFieldSearcher fs(0);
        SCOPED_TRACE("UTF8SubStringFieldSearcher");
        testUTF8SubStringFieldSearcher(fs);
        EXPECT_EQ(HitsList({{{0, 0}, {0, 0}}}), search_string(fs, "aa", "aaaa"));
    }
    {
        UTF8SubStringFieldSearcher fs(0);
        EXPECT_EQ(HitsList({{{0, 0}, {0, 2}}}), search_string(fs, "abc", "abc bcd abc"));
        fs.maxFieldLength(4);
        EXPECT_EQ(HitsList({{{0, 0}}}), search_string(fs, "abc", "abc bcd abc"));
    }
    {
        UTF8SubstringSnippetModifier fs(0);
        SCOPED_TRACE("UTF8SubstringSnippetModifier");
        testUTF8SubStringFieldSearcher(fs);
        // we don't have 1 term optimization
        EXPECT_EQ(HitsList({{{0, 0}, {0, 0}, {0, 0}}}), search_string(fs, "aa", "aaaa"));
    }
}

TEST(SearcherTest, utf8_substring_search_with_empty_term)
{
    UTF8SubStringFieldSearcher fs(0);
    testUTF8SubStringFieldSearcher(fs);
    EXPECT_EQ(HitsList({{}}), search_string(fs, "", "abc"));
    EXPECT_EQ(FieldInfoList({{0, 0, 0}}), search_string_field_info(fs, "", "abc"));
}

TEST(SearcherTest, utf8_suffix_search) {
    UTF8SuffixStringFieldSearcher fs(0);
    std::string field = "operators and operator overloading";
    EXPECT_EQ(no_hits,              search_string(fs, "rsand", field));
    EXPECT_EQ(HitsList({{{0, 2}}}), search_string(fs, "tor",   field));
    EXPECT_EQ(is_hit,               search_string(fs, "tors",  field));

    EXPECT_EQ(HitsList({{}, {}}),            search_string(fs, StringList{"an", "din"}, field));
    EXPECT_EQ(HitsList({{{0,1}}, {{0, 3}}}), search_string(fs, StringList{"nd", "g"},   field));
    testStringFieldInfo(fs);
}

TEST(SearcherTest, utf8_exact_match) {
    UTF8ExactStringFieldSearcher fs(0);
    // regular
    EXPECT_EQ(is_hit,  search_string(fs, "vespa",  "vespa"));
    EXPECT_EQ(no_hits, search_string(fs, "vespar", "vespa"));
    EXPECT_EQ(no_hits, search_string(fs, "vespa",  "vespar"));
    EXPECT_EQ(no_hits, search_string(fs, "vespa",  "vespa vespa"));
    EXPECT_EQ(no_hits, search_string(fs, "vesp",   "vespa"));
    EXPECT_EQ(is_hit,  search_string(fs, "vesp*",  "vespa"));
    EXPECT_EQ(is_hit,  search_string(fs, "hutte",  "hutte"));
    EXPECT_EQ(is_hit,  search_string(fs, "hütte",  "hütte"));
    EXPECT_EQ(no_hits, search_string(fs, "hutte",  "hütte"));
    EXPECT_EQ(no_hits, search_string(fs, "hütte",  "hutte"));
    EXPECT_EQ(no_hits, search_string(fs, "hütter", "hütte"));
    EXPECT_EQ(no_hits, search_string(fs, "hütte",  "hütter"));
}

TEST(SearcherTest, utf8_flexible_searcher_except_regex) {
    UTF8FlexibleStringFieldSearcher fs(0);
    // regular
    EXPECT_EQ(is_hit,  search_string(fs, "vespa", "vespa"));
    EXPECT_EQ(no_hits, search_string(fs, "vesp",  "vespa"));
    EXPECT_EQ(no_hits, search_string(fs, "esp",   "vespa"));
    EXPECT_EQ(no_hits, search_string(fs, "espa",  "vespa"));

    // prefix
    EXPECT_EQ(is_hit,  search_string(fs, "vesp*", "vespa"));
    fs.match_type(FieldSearcher::PREFIX);
    EXPECT_EQ(is_hit,  search_string(fs, "vesp",  "vespa"));

    // substring
    fs.match_type(FieldSearcher::REGULAR);
    EXPECT_EQ(is_hit,  search_string(fs, "*esp*", "vespa"));
    fs.match_type(FieldSearcher::SUBSTRING);
    EXPECT_EQ(is_hit,  search_string(fs, "esp",   "vespa"));

    // suffix
    fs.match_type(FieldSearcher::REGULAR);
    EXPECT_EQ(is_hit,  search_string(fs, "*espa", "vespa"));
    fs.match_type(FieldSearcher::SUFFIX);
    EXPECT_EQ(is_hit,  search_string(fs, "espa",  "vespa"));

    fs.match_type(FieldSearcher::REGULAR);
    testStringFieldInfo(fs);
}

TEST(SearcherTest, utf8_flexible_searcher_handles_regex_and_by_default_has_case_insensitive_partial_match_semantics) {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Note: the # term prefix is a magic term-as-regex symbol used only for tests in this file
    EXPECT_EQ(is_hit,  search_string(fs, "#abc", "ABC"));
    EXPECT_EQ(is_hit,  search_string(fs, "#bc", "ABC"));
    EXPECT_EQ(is_hit,  search_string(fs, "#ab", "ABC"));
    EXPECT_EQ(is_hit,  search_string(fs, "#[a-z]", "ABC"));
    EXPECT_EQ(is_hit,  search_string(fs, "#(zoid)(berg)", "why not zoidberg?"));
    EXPECT_EQ(no_hits, search_string(fs, "#[a-z]", "123"));
}

TEST(SearcherTest, utf8_flexible_searcher_handles_case_sensitive_regex_matching) {
    UTF8FlexibleStringFieldSearcher fs(0);
    fs.normalize_mode(Normalizing::NONE);
    EXPECT_EQ(no_hits, search_string(fs, "#abc",   "ABC"));
    EXPECT_EQ(is_hit,  search_string(fs, "#abc",   "abc"));
    EXPECT_EQ(is_hit,  search_string(fs, "#[A-Z]", "A"));
    EXPECT_EQ(is_hit,  search_string(fs, "#[A-Z]", "ABC"));
    EXPECT_EQ(no_hits, search_string(fs, "#[A-Z]", "abc"));
}

TEST(SearcherTest, utf8_flexible_searcher_handles_regexes_with_explicit_anchoring) {
    UTF8FlexibleStringFieldSearcher fs(0);
    EXPECT_EQ(is_hit,  search_string(fs, "#^foo",  "food"));
    EXPECT_EQ(no_hits, search_string(fs, "#^foo",  "afoo"));
    EXPECT_EQ(is_hit,  search_string(fs, "#foo$",  "afoo"));
    EXPECT_EQ(no_hits, search_string(fs, "#foo$",  "food"));
    EXPECT_EQ(is_hit,  search_string(fs, "#^foo$", "foo"));
    EXPECT_EQ(no_hits, search_string(fs, "#^foo$", "food"));
    EXPECT_EQ(no_hits, search_string(fs, "#^foo$", "oo"));
}

TEST(SearcherTest, utf8_flexible_searcher_regex_matching_treats_field_as_1_word) {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Match case
    EXPECT_EQ(FieldInfoList({{0, 1, 1}}), search_string_field_info(fs, "#.*", "foo bar baz"));
    // Mismatch case
    EXPECT_EQ(FieldInfoList({{0, 0, 1}}), search_string_field_info(fs, "#^zoid$", "foo bar baz"));
}

TEST(SearcherTest, utf8_flexible_searcher_handles_fuzzy_search_in_uncased_mode) {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Term syntax (only applies to these tests):
    //   %{k}term   => fuzzy match "term" with max edits k
    //   %{k,p}term => fuzzy match "term" with max edits k, prefix lock length p

    // DFA is used for k in {1, 2}
    EXPECT_EQ(is_hit,  search_string(fs, "%{1}abc",  "abc"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1}ABC",  "abc"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1}abc",  "ABC"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1}Abc",  "abd"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1}abc",  "ABCD"));
    EXPECT_EQ(no_hits, search_string(fs, "%{1}abc",  "abcde"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{2}abc",  "abcde"));
    EXPECT_EQ(no_hits, search_string(fs, "%{2}abc",  "xabcde"));
    // Fallback to non-DFA matcher when k not in {1, 2}
    EXPECT_EQ(is_hit,  search_string(fs, "%{3}abc",  "abc"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{3}abc",  "XYZ"));
    EXPECT_EQ(no_hits, search_string(fs, "%{3}abc",  "XYZ!"));
}

TEST(SearcherTest, utf8_flexible_searcher_handles_fuzzy_search_in_cased_mode) {
    UTF8FlexibleStringFieldSearcher fs(0);
    fs.normalize_mode(Normalizing::NONE);
    EXPECT_EQ(is_hit,  search_string(fs, "%{1}abc", "abc"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1}abc", "Abc"));
    EXPECT_EQ(no_hits, search_string(fs, "%{1}ABC", "abc"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{2}Abc", "abc"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{2}abc", "AbC"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{3}abc", "ABC"));
    EXPECT_EQ(no_hits, search_string(fs, "%{3}abc", "ABCD"));
}

TEST(SearcherTest, utf8_flexible_searcher_handles_fuzzy_search_with_prefix_locking) {
    UTF8FlexibleStringFieldSearcher fs(0);
    // DFA
    EXPECT_EQ(no_hits, search_string(fs, "%{1,4}zoid",     "zoi"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,4}zoid",     "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,4}zoid",     "ZOID"));
    EXPECT_EQ(no_hits, search_string(fs, "%{1,4}zoidberg", "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,4}zoidberg", "ZoidBerg"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,4}zoidberg", "ZoidBergg"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,4}zoidberg", "zoidborg"));
    EXPECT_EQ(no_hits, search_string(fs, "%{1,4}zoidberg", "zoidblergh"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{2,4}zoidberg", "zoidblergh"));
    // Fallback
    EXPECT_EQ(is_hit,  search_string(fs, "%{3,4}zoidberg", "zoidblergh"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{3,4}zoidberg", "zoidbooorg"));
    EXPECT_EQ(no_hits, search_string(fs, "%{3,4}zoidberg", "zoidzooorg"));

    fs.normalize_mode(Normalizing::NONE);
    // DFA
    EXPECT_EQ(no_hits, search_string(fs, "%{1,4}zoid",     "ZOID"));
    EXPECT_EQ(no_hits, search_string(fs, "%{1,4}ZOID",     "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,4}zoidberg", "zoidBerg")); // 1 edit
    EXPECT_EQ(no_hits, search_string(fs, "%{1,4}zoidberg", "zoidBblerg"));        // 2 edits, 1 max
    EXPECT_EQ(is_hit,  search_string(fs, "%{2,4}zoidberg", "zoidBblerg")); // 2 edits, 2 max
    // Fallback
    EXPECT_EQ(no_hits, search_string(fs, "%{3,4}zoidberg", "zoidBERG"));        // 4 edits, 3 max
    EXPECT_EQ(is_hit,  search_string(fs, "%{4,4}zoidberg", "zoidBERG")); // 4 edits, 4 max
}

TEST(SearcherTest, utf8_flexible_searcher_fuzzy_match_with_max_edits_zero_implies_exact_match) {
    UTF8FlexibleStringFieldSearcher fs(0);
    EXPECT_EQ(no_hits, search_string(fs, "%{0}zoid",   "zoi"));
    EXPECT_EQ(no_hits, search_string(fs, "%{0,4}zoid", "zoi"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{0}zoid",   "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{0}zoid",   "ZOID"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{0,4}zoid", "ZOID"));
    fs.normalize_mode(Normalizing::NONE);
    EXPECT_EQ(no_hits, search_string(fs, "%{0}zoid",   "ZOID"));
    EXPECT_EQ(no_hits, search_string(fs, "%{0,4}zoid", "ZOID"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{0}zoid",   "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{0,4}zoid", "zoid"));
}

TEST(SearcherTest, utf8_flexible_searcher_caps_oversized_fuzzy_prefix_length_to_term_length) {
    UTF8FlexibleStringFieldSearcher fs(0);
    // DFA
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,5}zoid",    "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{1,9001}zoid", "zoid"));
    EXPECT_EQ(no_hits, search_string(fs, "%{1,9001}zoid", "boid"));
    // Fallback
    EXPECT_EQ(is_hit,  search_string(fs, "%{0,5}zoid",    "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{5,5}zoid",    "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{0,9001}zoid", "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{5,9001}zoid", "zoid"));
    EXPECT_EQ(no_hits, search_string(fs, "%{5,9001}zoid", "boid"));
}

TEST(SearcherTest, utf8_flexible_searcher_fuzzy_matching_treats_field_as_1_word) {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Match case
    EXPECT_EQ(FieldInfoList({{0, 1, 1}}), search_string_field_info(fs, "%{1}foo bar baz", "foo jar baz"));
    // Mismatch case
    EXPECT_EQ(FieldInfoList({{0, 0, 1}}), search_string_field_info(fs, "%{1}foo", "foo bar baz"));
}

TEST(SearcherTest, utf8_flexible_searcher_supports_fuzzy_prefix_matching) {
    UTF8FlexibleStringFieldSearcher fs(0);
    EXPECT_EQ(is_hit,  search_string(fs, "%{p0}z",     "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p0}zo",    "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p0}zo",    "Zoid")); // uncased
    EXPECT_EQ(is_hit,  search_string(fs, "%{p0}Zo",    "zoid")); // uncased
    EXPECT_EQ(is_hit,  search_string(fs, "%{p0}zoid",  "zoid"));
    EXPECT_EQ(no_hits, search_string(fs, "%{p0}x",     "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p1}zo",    "boid"));
    EXPECT_EQ(no_hits, search_string(fs, "%{p1}zo",    "blid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p1}yam",   "hamburger"));
    EXPECT_EQ(no_hits, search_string(fs, "%{p1}yam",   "humbug"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p2}yam",   "humbug"));
    EXPECT_EQ(no_hits, search_string(fs, "%{p2}catfo", "dogfood"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p3}catfo", "dogfood"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p100}abcd", "anything you want")); // trivially matches
}

TEST(SearcherTest, utf8_flexible_searcher_supports_fuzzy_prefix_matching_combined_with_prefix_locking) {
    UTF8FlexibleStringFieldSearcher fs(0);
    EXPECT_EQ(is_hit,  search_string(fs, "%{p0,4}zoid",     "zoid"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p0,4}zoidber",  "zoidberg"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p1,4}zoidber",  "zoidburg"));
    EXPECT_EQ(no_hits, search_string(fs, "%{p1,4}zoidber",  "zoidblurgh"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p1,4}zoidbe",   "zoidblurgh"));
    EXPECT_EQ(no_hits, search_string(fs, "%{p1,4}zoidberg", "boidberg"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p1,4}zoidber",  "zoidburger"));
    EXPECT_EQ(no_hits, search_string(fs, "%{p1,4}zoidber",  "zoidbananas"));
    EXPECT_EQ(is_hit,  search_string(fs, "%{p2,4}zoidber",  "zoidbananas"));
}

TEST(SearcherTest, bool_search) {
    BoolFieldSearcher fs(0);
    EXPECT_EQ(is_hit,  search_bool(fs,     "true",  true));
    EXPECT_EQ(no_hits, search_bool(fs,     "true",  false));
    EXPECT_EQ(is_hit,  search_bool(fs,     "1",  true));
    EXPECT_EQ(no_hits, search_bool(fs,     "1",  false));
    EXPECT_EQ(no_hits, search_bool(fs,     "false",  true));
    EXPECT_EQ(is_hit,  search_bool(fs,     "false",  false));
    EXPECT_EQ(no_hits, search_bool(fs,     "0",  true));
    EXPECT_EQ(is_hit,  search_bool(fs,     "0",  false));
    EXPECT_EQ(hits_list({ true, false,  true}), search_bool(fs, StringList{"true", "false", "true"},  true));
    EXPECT_EQ(hits_list({false,  true, false}), search_bool(fs, StringList{"true", "false", "true"},  false));
}

TEST(SearcherTest, integer_search)
{
    IntFieldSearcher fs(0);
    EXPECT_EQ(is_hit,  search_int(fs,     "10",  10));
    EXPECT_EQ(no_hits, search_int(fs,      "9",  10));
    EXPECT_EQ(is_hit,  search_int(fs,     ">9",  10));
    EXPECT_EQ(no_hits, search_int(fs,     ">9",   9));
    EXPECT_EQ(is_hit,  search_int(fs,    "<11",  10));
    EXPECT_EQ(no_hits, search_int(fs,    "<11",  11));
    EXPECT_EQ(is_hit,  search_int(fs,    "-10", -10));
    EXPECT_EQ(no_hits, search_int(fs,     "10", -10));
    EXPECT_EQ(no_hits, search_int(fs,    "-10",  10));
    EXPECT_EQ(no_hits, search_int(fs,     "-9", -10));
    EXPECT_EQ(no_hits, search_int(fs,      "a",  10));
    EXPECT_EQ(is_hit,  search_int(fs, "[-5;5]",  -5));
    EXPECT_EQ(is_hit,  search_int(fs, "[-5;5]",   0));
    EXPECT_EQ(is_hit,  search_int(fs, "[-5;5]",   5));
    EXPECT_EQ(no_hits, search_int(fs, "[-5;5]",  -6));
    EXPECT_EQ(no_hits, search_int(fs, "[-5;5]",   6));

    EXPECT_EQ(hits_list({false, false}), search_int(fs, StringList{"9", "11"},  10));
    EXPECT_EQ(hits_list({false,  true}), search_int(fs, StringList{"9", "10"},  10));
    EXPECT_EQ(hits_list({ true,  true}), search_int(fs, StringList{"10", ">9"}, 10));

    EXPECT_EQ(HitsList({{{0, 0}, {2, 0}}}), search_int(fs, "10", {10, 20, 10, 30}));
    EXPECT_EQ(HitsList({{{0, 0}, {2, 0}},{{1, 0}}}), search_int(fs, StringList{"10", "20"}, {10, 20, 10, 30}));

    EXPECT_EQ(FieldInfoList({{0, 1, 1}}), search_int_field_info(fs, "10", 10));
    EXPECT_EQ(FieldInfoList({{0, 2, 4}}), search_int_field_info(fs, "10", {10, 20, 10, 30}));
    EXPECT_EQ(FieldInfoList({{0, 1, 1}, {0, 0, 1}}), search_int_field_info(fs, StringList{"10", "20"}, 10));
    EXPECT_EQ(FieldInfoList({{0, 2, 4}, {0, 1, 4}}),
              search_int_field_info(fs, StringList{"10", "20"}, {10, 20, 10, 30}));
}

TEST(SearcherTest, floating_point_search)
{
    FloatFieldSearcher fs(0);
    EXPECT_EQ(is_hit,  search_float(fs,         "10",    10));
    EXPECT_EQ(is_hit,  search_float(fs,       "10.5",  10.5));
    EXPECT_EQ(is_hit,  search_float(fs,      "-10.5", -10.5));
    EXPECT_EQ(is_hit,  search_float(fs,      ">10.5",  10.6));
    EXPECT_EQ(no_hits, search_float(fs,      ">10.5",  10.5));
    EXPECT_EQ(is_hit,  search_float(fs,      "<10.5",  10.4));
    EXPECT_EQ(no_hits, search_float(fs,      "<10.5",  10.5));
    EXPECT_EQ(no_hits, search_float(fs,       "10.4",  10.5));
    EXPECT_EQ(no_hits, search_float(fs,      "-10.4", -10.5));
    EXPECT_EQ(no_hits, search_float(fs,          "a",  10.5));
    EXPECT_EQ(is_hit,  search_float(fs, "[-5.5;5.5]",  -5.5));
    EXPECT_EQ(is_hit,  search_float(fs, "[-5.5;5.5]",     0));
    EXPECT_EQ(is_hit,  search_float(fs, "[-5.5;5.5]",   5.5));
    EXPECT_EQ(no_hits, search_float(fs, "[-5.5;5.5]",  -5.6));
    EXPECT_EQ(no_hits, search_float(fs, "[-5.5;5.5]",   5.6));

    EXPECT_EQ(hits_list({false, false}), search_float(fs, StringList{"10", "11"},      10.5));
    EXPECT_EQ(hits_list({false,  true}), search_float(fs, StringList{"10", "10.5"},    10.5));
    EXPECT_EQ(hits_list({ true,  true}), search_float(fs, StringList{">10.4", "10.5"}, 10.5));

    EXPECT_EQ(HitsList({{{0, 0}, {2, 0}}}), search_float(fs, "10.5", {10.5, 20.5, 10.5, 30.5}));
    EXPECT_EQ(HitsList({{{0, 0}, {2, 0}}, {{1, 0}}}),
              search_float(fs, StringList{"10.5", "20.5"}, {10.5, 20.5, 10.5, 30.5}));

    EXPECT_EQ(FieldInfoList({{0, 1, 1}}), search_float_field_info(fs, "10.5", 10.5));
    EXPECT_EQ(FieldInfoList({{0, 2, 4}}), search_float_field_info(fs, "10.5", {10.5, 20.5, 10.5, 30.5}));
    EXPECT_EQ(FieldInfoList({{0, 1, 1}, {0, 0, 1}}), search_float_field_info(fs, StringList{"10.5", "20.5"}, 10.5));
    EXPECT_EQ(FieldInfoList({{0, 2, 4}, {0, 1, 4}}),
              search_float_field_info(fs, StringList{"10.5", "20.5"}, {10.5, 20.5, 10.5, 30.5}));
}

TEST(SearcherTest, Snippet_modifier_search) {
    // ascii
    assertSnippetModifier("f", "foo", "\x1F""f\x1Foo");
    assertSnippetModifier("o", "foo", "f\x1Fo\x1F\x1Fo\x1F");
    assertSnippetModifier("r", "bar", "ba\x1Fr\x1F");
    assertSnippetModifier("foo", "foo foo", "\x1F""foo\x1F \x1F""foo\x1F");
    assertSnippetModifier("aa", "aaaaaa", "\x1F""aa\x1F\x1F""aa\x1F\x1F""aa\x1F");
    assertSnippetModifier("ab", "abcd\x1F""efgh", "\x1F""ab\x1F""cd\x1F""efgh");
    assertSnippetModifier("ef", "abcd\x1F""efgh", "abcd\x1F\x1F""ef\x1Fgh");
    assertSnippetModifier("fg", "abcd\x1F""efgh", "abcd\x1F""e\x1F""fg\x1Fh");
    // the separator overlapping the match is skipped
    assertSnippetModifier("cdef", "abcd\x1F""efgh", "ab\x1F""cdef\x1F""gh");
    // no hits
    assertSnippetModifier("bb", "aaaaaa", "aaaaaa");


    // multiple query terms
    assertSnippetModifier(StringList().add("ab").add("cd"), "abcd", "\x1F""ab\x1F\x1F""cd\x1F");
    // when we have overlap we only get the first match
    assertSnippetModifier(StringList().add("ab").add("bc"), "abcd", "\x1F""ab\x1F""cd");
    assertSnippetModifier(StringList().add("bc").add("ab"), "abcd", "\x1F""ab\x1F""cd");
    // the separator overlapping the match is skipped
    assertSnippetModifier(StringList().add("de").add("ef"), "abcd\x1F""efgh", "abc\x1F""de\x1F""fgh");

    // cjk
    assertSnippetModifier("\xe7\x9f\xb3", "\xe7\x9f\xb3\xe6\x98\x8e\xe5\x87\xb1\xe5\x9c\xa8",
                                      "\x1f\xe7\x9f\xb3\x1f\xe6\x98\x8e\xe5\x87\xb1\xe5\x9c\xa8");
    assertSnippetModifier("\xe6\x98\x8e\xe5\x87\xb1", "\xe7\x9f\xb3\xe6\x98\x8e\xe5\x87\xb1\xe5\x9c\xa8",
                                                      "\xe7\x9f\xb3\x1f\xe6\x98\x8e\xe5\x87\xb1\x1f\xe5\x9c\xa8");
    // the separator overlapping the match is skipped
    assertSnippetModifier("\xe6\x98\x8e\xe5\x87\xb1", "\xe7\x9f\xb3\xe6\x98\x8e\x1f\xe5\x87\xb1\xe5\x9c\xa8",
                                                      "\xe7\x9f\xb3\x1f\xe6\x98\x8e\xe5\x87\xb1\x1f\xe5\x9c\xa8");

    { // check that resizing works
        UTF8SubstringSnippetModifier mod(0);
        EXPECT_EQ(mod.getModifiedBuf().getLength(), 32u);
        EXPECT_EQ(mod.getModifiedBuf().getPos(), 0u);
        performSearch(mod, StringList().add("a"), StringFieldValue("aaaaaaaaaaaaaaaa"));
        EXPECT_EQ(mod.getModifiedBuf().getPos(), 16u + 2 * 16u);
        EXPECT_TRUE(mod.getModifiedBuf().getLength() >= mod.getModifiedBuf().getPos());
    }
}

TEST(SearcherTest, snippet_modifier) {
    { // string field value
        SnippetModifierSetup sms(StringList().add("ab"));
        // multiple invocations
        assertSnippetModifier(sms, StringFieldValue("ab"), "\x1F""ab\x1F");
        assertSnippetModifier(sms, StringFieldValue("xxxxabxxxxabxxxx"), "xxxx\x1F""ab\x1Fxxxx\x1F""ab\x1Fxxxx");
        assertSnippetModifier(sms, StringFieldValue("xxabxx"), "xx\x1F""ab\x1Fxx");
    }
    { // collection field value
        SnippetModifierSetup sms(StringList().add("ab"));
        // multiple invocations
        assertSnippetModifier(sms, getFieldValue(StringList().add("ab")), "\x1F""ab\x1F");
        assertSnippetModifier(sms, getFieldValue(StringList().add("xxabxx")), "xx\x1F""ab\x1Fxx");
        assertSnippetModifier(sms, getFieldValue(StringList().add("ab").add("xxabxx").add("xxxxxx")),
                              "\x1F""ab\x1F\x1E""xx\x1F""ab\x1F""xx\x1E""xxxxxx");
        assertSnippetModifier(sms, getFieldValue(StringList().add("cd").add("ef").add("gh")),
                              "cd\x1E""ef\x1E""gh");
    }
    { // check that resizing works
        SnippetModifierSetup sms(StringList().add("a"));
        EXPECT_EQ(sms.modifier.getValueBuf().getLength(), 32u);
        EXPECT_EQ(sms.modifier.getValueBuf().getPos(), 0u);
        sms.modifier.modify(StringFieldValue("aaaaaaaaaaaaaaaa"));
        EXPECT_EQ(sms.modifier.getValueBuf().getPos(), 16u + 2 * 16u);
        EXPECT_TRUE(sms.modifier.getValueBuf().getLength() >= sms.modifier.getValueBuf().getPos());
    }
}

TEST(SearcherTest, FieldSearchSpec_construction) {
    {
        FieldSearchSpec f;
        EXPECT_FALSE(f.valid());
        EXPECT_EQ(0u, f.id());
        EXPECT_EQ("", f.name());
        EXPECT_EQ(0x100000u, f.maxLength());
        EXPECT_EQ("", f.arg1());
        EXPECT_TRUE(Normalizing::LOWERCASE_AND_FOLD == f.normalize_mode());
    }
    {
        FieldSearchSpec f(7, "f0", Searchmethod::AUTOUTF8, Normalizing::LOWERCASE, "substring", 789);
        EXPECT_TRUE(f.valid());
        EXPECT_EQ(7u, f.id());
        EXPECT_EQ("f0", f.name());
        EXPECT_EQ(789u, f.maxLength());
        EXPECT_EQ(789u, f.searcher().maxFieldLength());
        EXPECT_EQ("substring", f.arg1());
        EXPECT_TRUE(Normalizing::LOWERCASE == f.normalize_mode());
    }
}

TEST(SearcherTest, FieldSearchSpec_reconfiguration_preserves_match_and_normalization_properties_for_new_searcher) {
    FieldSearchSpec f(7, "f0", Searchmethod::AUTOUTF8, Normalizing::NONE, "substring", 789);
    QueryNodeResultFactory qnrf;
    QueryTerm qt(qnrf.create(), "foo", "index", TermType::EXACTSTRINGTERM, Normalizing::LOWERCASE_AND_FOLD);
    // Match type, normalization mode and max length are all properties of the original spec
    // and should be propagated to the new searcher.
    f.reconfig(qt);
    EXPECT_EQ(f.searcher().match_type(), FieldSearcher::MatchType::SUBSTRING);
    EXPECT_EQ(f.searcher().normalize_mode(), Normalizing::NONE);
    EXPECT_EQ(f.searcher().maxFieldLength(), 789u);
}

TEST(SearcherTest, snippet_modifier_manager) {
    FieldSearchSpecMapT specMap;
    specMap[0] = FieldSearchSpec(0, "f0", Searchmethod::AUTOUTF8, Normalizing::LOWERCASE, "substring", 1000);
    specMap[1] = FieldSearchSpec(1, "f1", Searchmethod::AUTOUTF8, Normalizing::NONE, "", 1000);
    IndexFieldMapT indexMap;
    indexMap["i0"].push_back(0);
    indexMap["i1"].push_back(1);
    indexMap["i2"].push_back(0);
    indexMap["i2"].push_back(1);
    test::MockFieldSearcherEnv env;

    {
        SnippetModifierManager man;
        Query query(StringList().add("i0:foo"));
        man.setup(query.qtl, specMap, indexMap, *env.field_paths, env.query_env);
        assertQueryTerms(man, 0, StringList().add("foo"));
        assertQueryTerms(man, 1, StringList());
    }
    {
        SnippetModifierManager man;
        Query query(StringList().add("i1:foo"));
        man.setup(query.qtl, specMap, indexMap, *env.field_paths, env.query_env);
        assertQueryTerms(man, 0, StringList());
        assertQueryTerms(man, 1, StringList());
    }
    {
        SnippetModifierManager man;
        Query query(StringList().add("i1:*foo*"));
        man.setup(query.qtl, specMap, indexMap, *env.field_paths, env.query_env);
        assertQueryTerms(man, 0, StringList());
        assertQueryTerms(man, 1, StringList().add("foo"));
    }
    {
        SnippetModifierManager man;
        Query query(StringList().add("i2:foo").add("i2:*bar*"));
        man.setup(query.qtl, specMap, indexMap, *env.field_paths, env.query_env);
        assertQueryTerms(man, 0, StringList().add("foo").add("bar"));
        assertQueryTerms(man, 1, StringList().add("bar"));
    }
    { // check buffer sizes
        SnippetModifierManager man;
        Query query(StringList().add("i2:foo").add("i2:*bar*"));
        man.setup(query.qtl, specMap, indexMap, *env.field_paths, env.query_env);
        {
            auto * sm = static_cast<SnippetModifier *>(man.getModifiers().getModifier(0));
            UTF8SubstringSnippetModifier * searcher = sm->getSearcher().get();
            EXPECT_EQ(sm->getValueBuf().getLength(), 128u);
            EXPECT_EQ(searcher->getModifiedBuf().getLength(), 64u);
        }
        {
            auto * sm = static_cast<SnippetModifier *>(man.getModifiers().getModifier(1));
            UTF8SubstringSnippetModifier * searcher = sm->getSearcher().get();
            EXPECT_EQ(sm->getValueBuf().getLength(), 128u);
            EXPECT_EQ(searcher->getModifiedBuf().getLength(), 64u);
        }
    }
}

TEST(SearcherTest, Stripping_of_indexes)
{
    EXPECT_EQ("f", FieldSearchSpecMap::stripNonFields("f"));
    EXPECT_EQ("f", FieldSearchSpecMap::stripNonFields("f[0]"));
    EXPECT_EQ("f[a]", FieldSearchSpecMap::stripNonFields("f[a]"));

    EXPECT_EQ("f.value", FieldSearchSpecMap::stripNonFields("f{a}"));
    EXPECT_EQ("f.value", FieldSearchSpecMap::stripNonFields("f{a0}"));
    EXPECT_EQ("f{a 0}", FieldSearchSpecMap::stripNonFields("f{a 0}"));
    EXPECT_EQ("f.value", FieldSearchSpecMap::stripNonFields("f{\"a 0\"}"));
}

TEST(SearcherTest, counting_of_words) {
    EXPECT_EQ(0, count_words(""));
    EXPECT_EQ(0, count_words("?"));
    EXPECT_EQ(1, count_words("foo"));
    EXPECT_EQ(2, count_words("foo bar"));
    EXPECT_EQ(2, count_words("? foo bar"));
    EXPECT_EQ(2, count_words("foo bar ?"));

    // check that 'a' is counted as 1 word
    UTF8StrChrFieldSearcher fs(0);
    StringList field{"a", "aa bb cc"};
    EXPECT_EQ(HitsList({{{1, 1}}}), search_string(fs, "bb", field));
    EXPECT_EQ(HitsList({{{1, 1}},{}}), search_string(fs, StringList{"bb", "not"}, field));
}

TEST(SearcherTest, element_lengths)
{
    UTF8StrChrFieldSearcher fs(0);
    auto field = StringList().add("a").add("b a c").add("d a");
    auto query = StringList().add("a");
    auto qtv = performSearch(fs, query, getFieldValue(field));
    EXPECT_EQ(1u, qtv.size());
    auto& qt = *qtv[0];
    auto& hl = qt.getHitList();
    EXPECT_EQ(3u, hl.size());
    EXPECT_EQ(1u, hl[0].element_length());
    EXPECT_EQ(3u, hl[1].element_length());
    EXPECT_EQ(2u, hl[2].element_length());
}

std::string NormalizationInput = "test That Somehing happens with during NårmØlization";

void
verifyNormalization(Normalizing normalizing, size_t expected_len, const char * expected) {
    ucs4_t buf[256];
    TokenizeReader reader(reinterpret_cast<const search::byte *>(NormalizationInput.c_str()), NormalizationInput.size(), buf);
    while (reader.hasNext()) {
        reader.normalize(reader.next(), normalizing);
    }
    size_t len = reader.complete();
    EXPECT_EQ(expected_len, len);
    EXPECT_EQ(0,  Fast_UnicodeUtil::utf8cmp(expected, buf));
}

TEST(SearcherTest, test_normalizing) {
    verifyNormalization(Normalizing::NONE, 52, NormalizationInput.c_str());
    verifyNormalization(Normalizing::LOWERCASE, 52, "test that somehing happens with during nårmølization");
    verifyNormalization(Normalizing::LOWERCASE_AND_FOLD, 54, "test that somehing happens with during naarmoelization");
}

GTEST_MAIN_RUN_ALL_TESTS()
