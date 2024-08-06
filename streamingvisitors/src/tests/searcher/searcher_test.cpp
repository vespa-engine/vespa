// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/searchlib/query/streaming/fuzzy_term.h>
#include <vespa/searchlib/query/streaming/regexp_term.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
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
    Vector() : std::vector<T>() {}
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

class String
{
private:
    const std::string & _str;
public:
    explicit String(const std::string & str) : _str(str) {}
    bool operator==(const String & rhs) const {
        return _str == rhs._str;
    }
};

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
void assertNumeric(FieldSearcher &fs, const StringList &query, const FieldValue &fv, const BoolList &exp);
std::vector<QueryTerm::UP> performSearch(FieldSearcher &fs, const StringList &query, const FieldValue &fv);
void assertSearch(FieldSearcher &fs, const StringList &query, const FieldValue &fv, const HitsList &exp);
bool assertCountWords(size_t numWords, const std::string &field);
bool assertFieldInfo(FieldSearcher &fs, const StringList &query, const FieldValue &fv, const FieldInfoList &exp);

void assertString(StrChrFieldSearcher &fs, const StringList &query, const std::string &field, const HitsList &exp) {
    assertSearch(fs, query, StringFieldValue(field), exp);
}

void assertString(StrChrFieldSearcher &fs, const StringList &query, const StringList &field, const HitsList &exp) {
    assertSearch(fs, query, getFieldValue(field), exp);
}

void assertString(StrChrFieldSearcher &fs, const std::string &term, const std::string &field, const Hits &exp) {
    assertString(fs, StringList().add(term), field, HitsList().add(exp));
}
void assertString(StrChrFieldSearcher &fs, const std::string &term, const StringList &field, const Hits &exp) {
    assertString(fs, StringList().add(term), field, HitsList().add(exp));
}

void assertInt(IntFieldSearcher & fs, const StringList &query, int64_t field, const BoolList &exp) {
    assertNumeric(fs, query, LongFieldValue(field), exp);
}

void assertInt(IntFieldSearcher & fs, const std::string &term, int64_t field, bool exp) {
    assertInt(fs, StringList().add(term), field, BoolList().add(exp));
}

void assertBool(BoolFieldSearcher & fs, const StringList &query, bool field, const BoolList &exp) {
    assertNumeric(fs, query, BoolFieldValue(field), exp);
}
void assertBool(BoolFieldSearcher & fs, const std::string &term, bool field, bool exp) {
    assertBool(fs, StringList().add(term), field, BoolList().add(exp));
}

void assertInt(IntFieldSearcher & fs, const StringList &query, const LongList &field, const HitsList &exp) {
    assertSearch(fs, query, getFieldValue(field), exp);
}

void assertInt(IntFieldSearcher & fs, const std::string &term, const LongList &field, const Hits &exp) {
    assertInt(fs, StringList().add(term), field, HitsList().add(exp));
}

void assertFloat(FloatFieldSearcher & fs, const StringList &query, float field, const BoolList &exp) {
    assertNumeric(fs, query, FloatFieldValue(field), exp);
}

void assertFloat(FloatFieldSearcher & fs, const std::string &term, float field, bool exp) {
    assertFloat(fs, StringList().add(term), field, BoolList().add(exp));
}

void assertFloat(FloatFieldSearcher & fs, const StringList &query, const FloatList &field, const HitsList &exp) {
    assertSearch(fs, query, getFieldValue(field), exp);
}

void assertFloat(FloatFieldSearcher & fs, const std::string &term, const FloatList &field, const Hits &exp) {
    assertFloat(fs, StringList().add(term), field, HitsList().add(exp));
}

bool
assertFieldInfo(StrChrFieldSearcher &fs, const StringList &query, const std::string &fv, const FieldInfoList &exp) {
    return assertFieldInfo(fs, query, StringFieldValue(fv), exp);
}

bool
assertFieldInfo(StrChrFieldSearcher &fs, const StringList &query, const StringList &fv, const FieldInfoList &exp) {
    return assertFieldInfo(fs, query, getFieldValue(fv), exp);
}
bool
assertFieldInfo(StrChrFieldSearcher &fs, const std::string &term, const StringList &fv, const QTFieldInfo &exp) {
    return assertFieldInfo(fs, StringList().add(term), fv, FieldInfoList().add(exp));
}

bool
assertFieldInfo(StrChrFieldSearcher &fs, const std::string &term, const std::string &fv, const QTFieldInfo &exp) {
    return assertFieldInfo(fs, StringList().add(term), fv, FieldInfoList().add(exp));
}

void assertFieldInfo(IntFieldSearcher & fs, const StringList &query, int64_t fv, const FieldInfoList &exp) {
    assertFieldInfo(fs, query, LongFieldValue(fv), exp);
}

void assertFieldInfo(IntFieldSearcher & fs, const StringList &query, const LongList &fv, const FieldInfoList &exp) {
    assertFieldInfo(fs, query, getFieldValue(fv), exp);
}

void assertFieldInfo(IntFieldSearcher & fs, const std::string &term, int64_t fv, const QTFieldInfo &exp) {
    assertFieldInfo(fs, StringList().add(term), fv, FieldInfoList().add(exp));
}

void assertFieldInfo(IntFieldSearcher & fs, const std::string &term, const LongList &fv, const QTFieldInfo &exp) {
    assertFieldInfo(fs, StringList().add(term), fv, FieldInfoList().add(exp));
}

void assertFieldInfo(FloatFieldSearcher & fs, const StringList &query, float fv, const FieldInfoList &exp) {
    assertFieldInfo(fs, query, FloatFieldValue(fv), exp);
}

void
assertFieldInfo(FloatFieldSearcher & fs, const StringList &query, const FloatList &fv, const FieldInfoList &exp) {
    assertFieldInfo(fs, query, getFieldValue(fv), exp);
}

/** float field searcher **/
void assertFieldInfo(FloatFieldSearcher & fs, const std::string &term, float fv, const QTFieldInfo &exp) {
    assertFieldInfo(fs, StringList().add(term), fv, FieldInfoList().add(exp));
}

void assertFieldInfo(FloatFieldSearcher & fs, const std::string &term, const FloatList &fv, const QTFieldInfo &exp) {
    assertFieldInfo(fs, StringList().add(term), fv, FieldInfoList().add(exp));
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

void
assertNumeric(FieldSearcher & fs, const StringList & query, const FieldValue & fv, const BoolList & exp)
{
    HitsList hl;
    for (bool v : exp) {
        hl.push_back(v ? Hits().add({0, 0}) : Hits());
    }
    assertSearch(fs, query, fv, hl);
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

void
assertSearch(FieldSearcher & fs, const StringList & query, const FieldValue & fv, const HitsList & exp)
{
    auto qtv = performSearch(fs, query, fv);
    EXPECT_EQUAL(qtv.size(), exp.size());
    ASSERT_TRUE(qtv.size() == exp.size());
    for (size_t i = 0; i < qtv.size(); ++i) {
        const HitList & hl = qtv[i]->getHitList();
        EXPECT_EQUAL(hl.size(), exp[i].size());
        ASSERT_TRUE(hl.size() == exp[i].size());
        for (size_t j = 0; j < hl.size(); ++j) {
            EXPECT_EQUAL(0u, hl[j].field_id());
            EXPECT_EQUAL((size_t)hl[j].element_id(), exp[i][j].first);
            EXPECT_EQUAL((size_t)hl[j].position(), exp[i][j].second);
        }
    }
}

bool
assertFieldInfo(FieldSearcher & fs, const StringList & query,
                const FieldValue & fv, const FieldInfoList & exp)
{
    auto qtv = performSearch(fs, query, fv);
    if (!EXPECT_EQUAL(qtv.size(), exp.size())) return false;
    bool retval = true;
    for (size_t i = 0; i < qtv.size(); ++i) {
        if (!EXPECT_EQUAL(qtv[i]->getFieldInfo(0).getHitOffset(), exp[i].getHitOffset())) retval = false;
        if (!EXPECT_EQUAL(qtv[i]->getFieldInfo(0).getHitCount(), exp[i].getHitCount())) retval = false;
        if (!EXPECT_EQUAL(qtv[i]->getFieldInfo(0).getFieldLength(), exp[i].getFieldLength())) retval = false;
    }
    return retval;
}

void
assertSnippetModifier(const StringList & query, const std::string & fv, const std::string & exp)
{
    UTF8SubstringSnippetModifier mod(0);
    performSearch(mod, query, StringFieldValue(fv));
    EXPECT_EQUAL(mod.getModifiedBuf().getPos(), exp.size());
    std::string actual(mod.getModifiedBuf().getBuffer(), mod.getModifiedBuf().getPos());
    EXPECT_EQUAL(actual.size(), exp.size());
    EXPECT_EQUAL(actual, exp);
}

void assertSnippetModifier(SnippetModifierSetup & setup, const FieldValue & fv, const std::string & exp)
{
    FieldValue::UP mfv = setup.modifier.modify(fv);
    const auto & lfv = static_cast<const document::LiteralFieldValueB &>(*mfv.get());
    const std::string & actual = lfv.getValue();
    EXPECT_EQUAL(actual.size(), exp.size());
    EXPECT_EQUAL(actual, exp);
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
    EXPECT_EQUAL(searcher->getQueryTerms().size(), terms.size());
    ASSERT_TRUE(searcher->getQueryTerms().size() == terms.size());
    for (size_t i = 0; i < terms.size(); ++i) {
        EXPECT_EQUAL(std::string(searcher->getQueryTerms()[i]->getTerm()), terms[i]);
    }
}

bool assertCountWords(size_t numWords, const std::string & field)
{
    FieldRef ref(field.c_str(), field.size());
    return EXPECT_EQUAL(numWords, FieldSearcher::countWords(ref));
}

bool
testStringFieldInfo(StrChrFieldSearcher & fs)
{
    assertString(fs,    "foo", StringList().add("foo bar baz").add("foo bar").add("baz foo"), Hits().add({0, 0}).add({1, 0}).add({2, 1}));
    assertString(fs,    StringList().add("foo").add("bar"), StringList().add("foo bar baz").add("foo bar").add("baz foo"),
                 HitsList().add(Hits().add({0, 0}).add({1, 0}).add({2, 1})).add(Hits().add({0, 1}).add({1, 1})));

    bool retval = true;
    if (!EXPECT_TRUE(assertFieldInfo(fs, "foo", "foo", QTFieldInfo(0, 1, 1)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, "bar", "foo", QTFieldInfo(0, 0, 1)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, "foo", "foo bar baz", QTFieldInfo(0, 1, 3)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, "bar", "foo bar baz", QTFieldInfo(0, 1, 3)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, "baz", "foo bar baz", QTFieldInfo(0, 1, 3)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, "qux", "foo bar baz", QTFieldInfo(0, 0, 3)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, "foo", "foo foo foo", QTFieldInfo(0, 3, 3)))) retval = false;
    // query term size > last term size
    if (!EXPECT_TRUE(assertFieldInfo(fs, "runner", "Road Runner Disco", QTFieldInfo(0, 1, 3)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, StringList().add("roadrun").add("runner"), "Road Runner Disco",
                                     FieldInfoList().add(QTFieldInfo(0, 0, 3)).add(QTFieldInfo(0, 1, 3))))) retval = false;
    // multiple terms
    if (!EXPECT_TRUE(assertFieldInfo(fs, "foo", StringList().add("foo bar baz").add("foo bar"),
                                     QTFieldInfo(0, 2, 5)))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, StringList().add("foo").add("baz"), "foo bar baz",
                                     FieldInfoList().add(QTFieldInfo(0, 1, 3)).add(QTFieldInfo(0, 1, 3))))) retval = false;
    if (!EXPECT_TRUE(assertFieldInfo(fs, StringList().add("foo").add("baz"), StringList().add("foo bar baz").add("foo bar"),
                                     FieldInfoList().add(QTFieldInfo(0, 2, 5)).add(QTFieldInfo(0, 1, 5))))) retval = false;
    return retval;
}
bool
testStrChrFieldSearcher(StrChrFieldSearcher & fs)
{
    std::string field = "operators and operator overloading with utf8 char oe = \xc3\x98";
    assertString(fs, "oper",  field, Hits());
    assertString(fs, "tor",   field, Hits());
    assertString(fs, "oper*", field, Hits().add({0, 0}).add({0, 2}));
    assertString(fs, "and",   field, Hits().add({0, 1}));

    assertString(fs, StringList().add("oper").add("tor"), field, HitsList().add(Hits()).add(Hits()));
    assertString(fs, StringList().add("and").add("overloading"), field, HitsList().add(Hits().add({0, 1})).add(Hits().add({0, 3})));

    fs.match_type(FieldSearcher::PREFIX);
    assertString(fs, "oper",  field, Hits().add({0, 0}).add({0, 2}));
    assertString(fs, StringList().add("oper").add("tor"), field, HitsList().add(Hits().add({0, 0}).add({0, 2})).add(Hits()));

    fs.match_type(FieldSearcher::REGULAR);
    if (!EXPECT_TRUE(testStringFieldInfo(fs))) return false;

    { // test handling of several underscores
        StringList query = StringList().add("foo").add("bar");
        HitsList exp = HitsList().add(Hits().add({0, 0})).add(Hits().add({0, 1}));
        assertString(fs, query, "foo_bar", exp);
        assertString(fs, query, "foo__bar", exp);
        assertString(fs, query, "foo___bar", exp);
        assertString(fs, query, "foo________bar", exp);
        assertString(fs, query, "foo____________________bar", exp);
        assertString(fs, query, "________________________________________foo________________________________________bar________________________________________", exp);
        query = StringList().add("foo").add("thisisaveryveryverylongword");
        assertString(fs, query, "foo____________________thisisaveryveryverylongword", exp);

        assertString(fs, "bar", "foo                    bar", Hits().add({0, 1}));
        assertString(fs, "bar", "foo____________________bar", Hits().add({0, 1}));
        assertString(fs, "bar", "foo____________________thisisaveryveryverylongword____________________bar", Hits().add({0, 2}));
    }
    return true;
}

void check_fuzzy_param_parsing(std::string_view term, std::string_view exp_term,
                               uint8_t exp_max_edits, uint32_t exp_prefix_length, bool exp_prefix)
{
    uint8_t max_edits = 0;
    uint32_t prefix_length = 0;
    bool prefix = false;
    std::string_view out;

    std::tie(max_edits, prefix_length, prefix, out) = parse_fuzzy_params(term);
    EXPECT_EQUAL(static_cast<uint32_t>(max_edits), static_cast<uint32_t>(exp_max_edits)); // don't print as char...
    EXPECT_EQUAL(prefix_length, exp_prefix_length);
    EXPECT_EQUAL(prefix, exp_prefix);
    EXPECT_EQUAL(out, exp_term);

}

TEST("parsing of test-only fuzzy term params can extract expected values") {
    check_fuzzy_param_parsing("myterm",        "myterm", 2, 0,  false);
    check_fuzzy_param_parsing("{3}myterm",     "myterm", 3, 0,  false);
    check_fuzzy_param_parsing("{p}myterm",     "myterm", 2, 0,  true);
    check_fuzzy_param_parsing("{p1}myterm",    "myterm", 1, 0,  true);
    check_fuzzy_param_parsing("{2,70}myterm",  "myterm", 2, 70, false);
    check_fuzzy_param_parsing("{p2,70}myterm", "myterm", 2, 70, true);
}

TEST("verify correct term parsing") {
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

TEST("suffix matching") {
    EXPECT_EQUAL(assertMatchTermSuffix("a",      "vespa"), true);
    EXPECT_EQUAL(assertMatchTermSuffix("spa",    "vespa"), true);
    EXPECT_EQUAL(assertMatchTermSuffix("vespa",  "vespa"), true);
    EXPECT_EQUAL(assertMatchTermSuffix("vvespa", "vespa"), false);
    EXPECT_EQUAL(assertMatchTermSuffix("fspa",   "vespa"), false);
    EXPECT_EQUAL(assertMatchTermSuffix("v",      "vespa"), false);
}

TEST("Test basic strchrfield searchers") {
    {
        UTF8StrChrFieldSearcher fs(0);
        EXPECT_TRUE(testStrChrFieldSearcher(fs));
    }
    {
        FUTF8StrChrFieldSearcher fs(0);
        EXPECT_TRUE(testStrChrFieldSearcher(fs));
    }
}

bool
testUTF8SubStringFieldSearcher(StrChrFieldSearcher & fs)
{
    std::string field = "operators and operator overloading";
    assertString(fs, "rsand", field, Hits());
    assertString(fs, "ove",   field, Hits().add({0, 3}));
    assertString(fs, "ing",   field, Hits().add({0, 3}));
    assertString(fs, "era",   field, Hits().add({0, 0}).add({0, 2}));
    assertString(fs, "a",     field, Hits().add({0, 0}).add({0, 1}).add({0, 2}).add({0, 3}));

    assertString(fs, StringList().add("dn").add("gn"), field, HitsList().add(Hits()).add(Hits()));
    assertString(fs, StringList().add("ato").add("load"), field, HitsList().add(Hits().add({0, 0}).add({0, 2})).add(Hits().add({0, 3})));

    assertString(fs, StringList().add("aa").add("ab"), "aaaab",
                 HitsList().add(Hits().add({0, 0}).add({0, 0}).add({0, 0})).add(Hits().add({0, 0})));

    if (!EXPECT_TRUE(testStringFieldInfo(fs))) return false;
    return true;
}

TEST("utf8 substring search") {
    {
        UTF8SubStringFieldSearcher fs(0);
        EXPECT_TRUE(testUTF8SubStringFieldSearcher(fs));
        assertString(fs, "aa", "aaaa", Hits().add({0, 0}).add({0, 0}));
    }
    {
        UTF8SubStringFieldSearcher fs(0);
        EXPECT_TRUE(testUTF8SubStringFieldSearcher(fs));
        assertString(fs, "abc", "abc bcd abc", Hits().add({0, 0}).add({0, 2}));
        fs.maxFieldLength(4);
        assertString(fs, "abc", "abc bcd abc", Hits().add({0, 0}));
    }
    {
        UTF8SubstringSnippetModifier fs(0);
        EXPECT_TRUE(testUTF8SubStringFieldSearcher(fs));
        // we don't have 1 term optimization
        assertString(fs, "aa", "aaaa", Hits().add({0, 0}).add({0, 0}).add({0, 0}));
    }
}

TEST("utf8 substring search with empty term")
{
    UTF8SubStringFieldSearcher fs(0);
    EXPECT_TRUE(testUTF8SubStringFieldSearcher(fs));
    assertString(fs, "", "abc", Hits());
    assertFieldInfo(fs, "", "abc", QTFieldInfo().setFieldLength(0));
}

Hits is_hit() {
    return Hits().add({0, 0});
}

Hits no_hits() {
    return {};
}

TEST("utf8 suffix search") {
    UTF8SuffixStringFieldSearcher fs(0);
    std::string field = "operators and operator overloading";
    TEST_DO(assertString(fs, "rsand", field, Hits()));
    TEST_DO(assertString(fs, "tor",   field, Hits().add({0, 2})));
    TEST_DO(assertString(fs, "tors",  field, Hits().add({0, 0})));

    TEST_DO(assertString(fs, StringList().add("an").add("din"), field, HitsList().add(Hits()).add(Hits())));
    TEST_DO(assertString(fs, StringList().add("nd").add("g"), field, HitsList().add(Hits().add({0, 1})).add(Hits().add({0, 3}))));

    EXPECT_TRUE(testStringFieldInfo(fs));
}

TEST("utf8 exact match") {
    UTF8ExactStringFieldSearcher fs(0);
    // regular
    TEST_DO(assertString(fs, "vespa", "vespa", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "vespar", "vespa", Hits()));
    TEST_DO(assertString(fs, "vespa", "vespar", Hits()));
    TEST_DO(assertString(fs, "vespa", "vespa vespa", Hits()));
    TEST_DO(assertString(fs, "vesp",  "vespa", Hits()));
    TEST_DO(assertString(fs, "vesp*",  "vespa", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "hutte",  "hutte", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "hütte",  "hütte", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "hutte",  "hütte", Hits()));
    TEST_DO(assertString(fs, "hütte",  "hutte", Hits()));
    TEST_DO(assertString(fs, "hütter", "hütte", Hits()));
    TEST_DO(assertString(fs, "hütte",  "hütter", Hits()));
}

TEST("utf8 flexible searcher (except regex)"){
    UTF8FlexibleStringFieldSearcher fs(0);
    // regular
    assertString(fs, "vespa", "vespa", Hits().add({0, 0}));
    assertString(fs, "vesp",  "vespa", Hits());
    assertString(fs, "esp",   "vespa", Hits());
    assertString(fs, "espa",  "vespa", Hits());

    // prefix
    assertString(fs, "vesp*",  "vespa", Hits().add({0, 0}));
    fs.match_type(FieldSearcher::PREFIX);
    assertString(fs, "vesp",   "vespa", Hits().add({0, 0}));

    // substring
    fs.match_type(FieldSearcher::REGULAR);
    assertString(fs, "*esp*",  "vespa", Hits().add({0, 0}));
    fs.match_type(FieldSearcher::SUBSTRING);
    assertString(fs, "esp",  "vespa", Hits().add({0, 0}));

    // suffix
    fs.match_type(FieldSearcher::REGULAR);
    assertString(fs, "*espa",  "vespa", Hits().add({0, 0}));
    fs.match_type(FieldSearcher::SUFFIX);
    assertString(fs, "espa",  "vespa", Hits().add({0, 0}));

    fs.match_type(FieldSearcher::REGULAR);
    EXPECT_TRUE(testStringFieldInfo(fs));
}

TEST("utf8 flexible searcher handles regex and by default has case-insensitive partial match semantics") {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Note: the # term prefix is a magic term-as-regex symbol used only for tests in this file
    TEST_DO(assertString(fs, "#abc",   "ABC", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#bc",    "ABC", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#ab",    "ABC", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#[a-z]", "ABC", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#(zoid)(berg)", "why not zoidberg?", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#[a-z]", "123", Hits()));
}

TEST("utf8 flexible searcher handles case-sensitive regex matching") {
    UTF8FlexibleStringFieldSearcher fs(0);
    fs.normalize_mode(Normalizing::NONE);
    TEST_DO(assertString(fs, "#abc",   "ABC", Hits()));
    TEST_DO(assertString(fs, "#abc",   "abc", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#[A-Z]",   "A", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#[A-Z]", "ABC", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#[A-Z]", "abc", Hits()));
}

TEST("utf8 flexible searcher handles regexes with explicit anchoring") {
    UTF8FlexibleStringFieldSearcher fs(0);
    TEST_DO(assertString(fs, "#^foo",  "food", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#^foo",  "afoo", Hits()));
    TEST_DO(assertString(fs, "#foo$",  "afoo", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#foo$",  "food", Hits()));
    TEST_DO(assertString(fs, "#^foo$", "foo",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "#^foo$", "food", Hits()));
    TEST_DO(assertString(fs, "#^foo$", "oo",   Hits()));
}

TEST("utf8 flexible searcher regex matching treats field as 1 word") {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Match case
    TEST_DO(assertFieldInfo(fs, "#.*", "foo bar baz", QTFieldInfo(0, 1, 1)));
    // Mismatch case
    TEST_DO(assertFieldInfo(fs, "#^zoid$", "foo bar baz", QTFieldInfo(0, 0, 1)));
}

TEST("utf8 flexible searcher handles fuzzy search in uncased mode") {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Term syntax (only applies to these tests):
    //   %{k}term   => fuzzy match "term" with max edits k
    //   %{k,p}term => fuzzy match "term" with max edits k, prefix lock length p

    // DFA is used for k in {1, 2}
    TEST_DO(assertString(fs, "%{1}abc",  "abc",    Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1}ABC",  "abc",    Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1}abc",  "ABC",    Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1}Abc",  "abd",    Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1}abc",  "ABCD",   Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1}abc",  "abcde",  Hits()));
    TEST_DO(assertString(fs, "%{2}abc",  "abcde",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{2}abc",  "xabcde", Hits()));
    // Fallback to non-DFA matcher when k not in {1, 2}
    TEST_DO(assertString(fs, "%{3}abc",  "abc",   Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{3}abc",  "XYZ",   Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{3}abc",  "XYZ!",  Hits()));
}

TEST("utf8 flexible searcher handles fuzzy search in cased mode") {
    UTF8FlexibleStringFieldSearcher fs(0);
    fs.normalize_mode(Normalizing::NONE);
    TEST_DO(assertString(fs, "%{1}abc", "abc",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1}abc", "Abc",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1}ABC", "abc",  Hits()));
    TEST_DO(assertString(fs, "%{2}Abc", "abc",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{2}abc", "AbC",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{3}abc", "ABC",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{3}abc", "ABCD", Hits()));
}

TEST("utf8 flexible searcher handles fuzzy search with prefix locking") {
    UTF8FlexibleStringFieldSearcher fs(0);
    // DFA
    TEST_DO(assertString(fs, "%{1,4}zoid",     "zoi",        Hits()));
    TEST_DO(assertString(fs, "%{1,4}zoid",     "zoid",       Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1,4}zoid",     "ZOID",       Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1,4}zoidberg", "zoid",       Hits()));
    TEST_DO(assertString(fs, "%{1,4}zoidberg", "ZoidBerg",   Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1,4}zoidberg", "ZoidBergg",  Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1,4}zoidberg", "zoidborg",   Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1,4}zoidberg", "zoidblergh", Hits()));
    TEST_DO(assertString(fs, "%{2,4}zoidberg", "zoidblergh", Hits().add({0, 0})));
    // Fallback
    TEST_DO(assertString(fs, "%{3,4}zoidberg", "zoidblergh", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{3,4}zoidberg", "zoidbooorg", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{3,4}zoidberg", "zoidzooorg", Hits()));

    fs.normalize_mode(Normalizing::NONE);
    // DFA
    TEST_DO(assertString(fs, "%{1,4}zoid",     "ZOID",       Hits()));
    TEST_DO(assertString(fs, "%{1,4}ZOID",     "zoid",       Hits()));
    TEST_DO(assertString(fs, "%{1,4}zoidberg", "zoidBerg",   Hits().add({0, 0}))); // 1 edit
    TEST_DO(assertString(fs, "%{1,4}zoidberg", "zoidBblerg", Hits()));        // 2 edits, 1 max
    TEST_DO(assertString(fs, "%{2,4}zoidberg", "zoidBblerg", Hits().add({0, 0}))); // 2 edits, 2 max
    // Fallback
    TEST_DO(assertString(fs, "%{3,4}zoidberg", "zoidBERG",   Hits()));        // 4 edits, 3 max
    TEST_DO(assertString(fs, "%{4,4}zoidberg", "zoidBERG",   Hits().add({0, 0}))); // 4 edits, 4 max
}

TEST("utf8 flexible searcher fuzzy match with max_edits=0 implies exact match") {
    UTF8FlexibleStringFieldSearcher fs(0);
    TEST_DO(assertString(fs, "%{0}zoid",   "zoi",  Hits()));
    TEST_DO(assertString(fs, "%{0,4}zoid", "zoi",  Hits()));
    TEST_DO(assertString(fs, "%{0}zoid",   "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{0}zoid",   "ZOID", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{0,4}zoid", "ZOID", Hits().add({0, 0})));
    fs.normalize_mode(Normalizing::NONE);
    TEST_DO(assertString(fs, "%{0}zoid",   "ZOID", Hits()));
    TEST_DO(assertString(fs, "%{0,4}zoid", "ZOID", Hits()));
    TEST_DO(assertString(fs, "%{0}zoid",   "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{0,4}zoid", "zoid", Hits().add({0, 0})));
}

TEST("utf8 flexible searcher caps oversized fuzzy prefix length to term length") {
    UTF8FlexibleStringFieldSearcher fs(0);
    // DFA
    TEST_DO(assertString(fs, "%{1,5}zoid",    "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1,9001}zoid", "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{1,9001}zoid", "boid", Hits()));
    // Fallback
    TEST_DO(assertString(fs, "%{0,5}zoid",    "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{5,5}zoid",    "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{0,9001}zoid", "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{5,9001}zoid", "zoid", Hits().add({0, 0})));
    TEST_DO(assertString(fs, "%{5,9001}zoid", "boid", Hits()));
}

TEST("utf8 flexible searcher fuzzy matching treats field as 1 word") {
    UTF8FlexibleStringFieldSearcher fs(0);
    // Match case
    TEST_DO(assertFieldInfo(fs, "%{1}foo bar baz", "foo jar baz", QTFieldInfo(0, 1, 1)));
    // Mismatch case
    TEST_DO(assertFieldInfo(fs, "%{1}foo", "foo bar baz", QTFieldInfo(0, 0, 1)));
}

TEST("utf8 flexible searcher supports fuzzy prefix matching") {
    UTF8FlexibleStringFieldSearcher fs(0);
    TEST_DO(assertString(fs, "%{p0}z",     "zoid",      is_hit()));
    TEST_DO(assertString(fs, "%{p0}zo",    "zoid",      is_hit()));
    TEST_DO(assertString(fs, "%{p0}zo",    "Zoid",      is_hit())); // uncased
    TEST_DO(assertString(fs, "%{p0}Zo",    "zoid",      is_hit())); // uncased
    TEST_DO(assertString(fs, "%{p0}zoid",  "zoid",      is_hit()));
    TEST_DO(assertString(fs, "%{p0}x",     "zoid",      no_hits()));
    TEST_DO(assertString(fs, "%{p1}zo",    "boid",      is_hit()));
    TEST_DO(assertString(fs, "%{p1}zo",    "blid",      no_hits()));
    TEST_DO(assertString(fs, "%{p1}yam",   "hamburger", is_hit()));
    TEST_DO(assertString(fs, "%{p1}yam",   "humbug",    no_hits()));
    TEST_DO(assertString(fs, "%{p2}yam",   "humbug",    is_hit()));
    TEST_DO(assertString(fs, "%{p2}catfo", "dogfood",   no_hits()));
    TEST_DO(assertString(fs, "%{p3}catfo", "dogfood",   is_hit()));
    TEST_DO(assertString(fs, "%{p100}abcd", "anything you want", is_hit())); // trivially matches
}

TEST("utf8 flexible searcher supports fuzzy prefix matching combined with prefix locking") {
    UTF8FlexibleStringFieldSearcher fs(0);
    TEST_DO(assertString(fs, "%{p0,4}zoid",     "zoid",        is_hit()));
    TEST_DO(assertString(fs, "%{p0,4}zoidber",  "zoidberg",    is_hit()));
    TEST_DO(assertString(fs, "%{p1,4}zoidber",  "zoidburg",    is_hit()));
    TEST_DO(assertString(fs, "%{p1,4}zoidber",  "zoidblurgh",  no_hits()));
    TEST_DO(assertString(fs, "%{p1,4}zoidbe",   "zoidblurgh",  is_hit()));
    TEST_DO(assertString(fs, "%{p1,4}zoidberg", "boidberg",    no_hits()));
    TEST_DO(assertString(fs, "%{p1,4}zoidber",  "zoidburger",  is_hit()));
    TEST_DO(assertString(fs, "%{p1,4}zoidber",  "zoidbananas", no_hits()));
    TEST_DO(assertString(fs, "%{p2,4}zoidber",  "zoidbananas", is_hit()));
}

TEST("bool search") {
    BoolFieldSearcher fs(0);
    TEST_DO(assertBool(fs,     "true",  true, true));
    TEST_DO(assertBool(fs,     "true",  false, false));
    TEST_DO(assertBool(fs,     "1",  true, true));
    TEST_DO(assertBool(fs,     "1",  false, false));
    TEST_DO(assertBool(fs,     "false",  true, false));
    TEST_DO(assertBool(fs,     "false",  false, true));
    TEST_DO(assertBool(fs,     "0",  true, false));
    TEST_DO(assertBool(fs,     "0",  false, true));
    TEST_DO(assertBool(fs, StringList().add("true").add("false").add("true"),  true, BoolList().add(true).add(false).add(true)));
    TEST_DO(assertBool(fs, StringList().add("true").add("false").add("true"),  false, BoolList().add(false).add(true).add(false)));
}

TEST("integer search")
{
    IntFieldSearcher fs(0);
    TEST_DO(assertInt(fs,     "10",  10, true));
    TEST_DO(assertInt(fs,      "9",  10, false));
    TEST_DO(assertInt(fs,     ">9",  10, true));
    TEST_DO(assertInt(fs,     ">9",   9, false));
    TEST_DO(assertInt(fs,    "<11",  10, true));
    TEST_DO(assertInt(fs,    "<11",  11, false));
    TEST_DO(assertInt(fs,    "-10", -10, true));
    TEST_DO(assertInt(fs,     "10", -10, false));
    TEST_DO(assertInt(fs,    "-10",  10, false));
    TEST_DO(assertInt(fs,     "-9", -10, false));
    TEST_DO(assertInt(fs,      "a",  10, false));
    TEST_DO(assertInt(fs, "[-5;5]",  -5, true));
    TEST_DO(assertInt(fs, "[-5;5]",   0, true));
    TEST_DO(assertInt(fs, "[-5;5]",   5, true));
    TEST_DO(assertInt(fs, "[-5;5]",  -6, false));
    TEST_DO(assertInt(fs, "[-5;5]",   6, false));

    TEST_DO(assertInt(fs, StringList().add("9").add("11"),  10, BoolList().add(false).add(false)));
    TEST_DO(assertInt(fs, StringList().add("9").add("10"),  10, BoolList().add(false).add(true)));
    TEST_DO(assertInt(fs, StringList().add("10").add(">9"), 10, BoolList().add(true).add(true)));

    TEST_DO(assertInt(fs, "10", LongList().add(10).add(20).add(10).add(30), Hits().add({0, 0}).add({2, 0})));
    TEST_DO(assertInt(fs, StringList().add("10").add("20"), LongList().add(10).add(20).add(10).add(30),
                      HitsList().add(Hits().add({0, 0}).add({2, 0})).add(Hits().add({1, 0}))));

    TEST_DO(assertFieldInfo(fs, "10", 10, QTFieldInfo(0, 1, 1)));
    TEST_DO(assertFieldInfo(fs, "10", LongList().add(10).add(20).add(10).add(30), QTFieldInfo(0, 2, 4)));
    TEST_DO(assertFieldInfo(fs, StringList().add("10").add("20"), 10,
                            FieldInfoList().add(QTFieldInfo(0, 1, 1)).add(QTFieldInfo(0, 0, 1))));
    TEST_DO(assertFieldInfo(fs, StringList().add("10").add("20"), LongList().add(10).add(20).add(10).add(30),
                            FieldInfoList().add(QTFieldInfo(0, 2, 4)).add(QTFieldInfo(0, 1, 4))));
}

TEST("floating point search")
{
    FloatFieldSearcher fs(0);
    TEST_DO(assertFloat(fs,         "10",    10, true));
    TEST_DO(assertFloat(fs,       "10.5",  10.5, true));
    TEST_DO(assertFloat(fs,      "-10.5", -10.5, true));
    TEST_DO(assertFloat(fs,      ">10.5",  10.6, true));
    TEST_DO(assertFloat(fs,      ">10.5",  10.5, false));
    TEST_DO(assertFloat(fs,      "<10.5",  10.4, true));
    TEST_DO(assertFloat(fs,      "<10.5",  10.5, false));
    TEST_DO(assertFloat(fs,       "10.4",  10.5, false));
    TEST_DO(assertFloat(fs,      "-10.4", -10.5, false));
    TEST_DO(assertFloat(fs,          "a",  10.5, false));
    TEST_DO(assertFloat(fs, "[-5.5;5.5]",  -5.5, true));
    TEST_DO(assertFloat(fs, "[-5.5;5.5]",     0, true));
    TEST_DO(assertFloat(fs, "[-5.5;5.5]",   5.5, true));
    TEST_DO(assertFloat(fs, "[-5.5;5.5]",  -5.6, false));
    TEST_DO(assertFloat(fs, "[-5.5;5.5]",   5.6, false));

    TEST_DO(assertFloat(fs, StringList().add("10").add("11"),      10.5, BoolList().add(false).add(false)));
    TEST_DO(assertFloat(fs, StringList().add("10").add("10.5"),    10.5, BoolList().add(false).add(true)));
    TEST_DO(assertFloat(fs, StringList().add(">10.4").add("10.5"), 10.5, BoolList().add(true).add(true)));

    TEST_DO(assertFloat(fs, "10.5", FloatList().add(10.5).add(20.5).add(10.5).add(30.5), Hits().add({0, 0}).add({2, 0})));
    TEST_DO(assertFloat(fs, StringList().add("10.5").add("20.5"), FloatList().add(10.5).add(20.5).add(10.5).add(30.5),
                    HitsList().add(Hits().add({0, 0}).add({2, 0})).add(Hits().add({1, 0}))));

    TEST_DO(assertFieldInfo(fs, "10.5", 10.5, QTFieldInfo(0, 1, 1)));
    TEST_DO(assertFieldInfo(fs, "10.5", FloatList().add(10.5).add(20.5).add(10.5).add(30.5), QTFieldInfo(0, 2, 4)));
    TEST_DO(assertFieldInfo(fs, StringList().add("10.5").add("20.5"), 10.5,
                    FieldInfoList().add(QTFieldInfo(0, 1, 1)).add(QTFieldInfo(0, 0, 1))));
    TEST_DO(assertFieldInfo(fs, StringList().add("10.5").add("20.5"), FloatList().add(10.5).add(20.5).add(10.5).add(30.5),
                    FieldInfoList().add(QTFieldInfo(0, 2, 4)).add(QTFieldInfo(0, 1, 4))));
}

TEST("Snippet modifier search") {
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
        EXPECT_EQUAL(mod.getModifiedBuf().getLength(), 32u);
        EXPECT_EQUAL(mod.getModifiedBuf().getPos(), 0u);
        performSearch(mod, StringList().add("a"), StringFieldValue("aaaaaaaaaaaaaaaa"));
        EXPECT_EQUAL(mod.getModifiedBuf().getPos(), 16u + 2 * 16u);
        EXPECT_TRUE(mod.getModifiedBuf().getLength() >= mod.getModifiedBuf().getPos());
    }
}

TEST("snippet modifier") {
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
        EXPECT_EQUAL(sms.modifier.getValueBuf().getLength(), 32u);
        EXPECT_EQUAL(sms.modifier.getValueBuf().getPos(), 0u);
        sms.modifier.modify(StringFieldValue("aaaaaaaaaaaaaaaa"));
        EXPECT_EQUAL(sms.modifier.getValueBuf().getPos(), 16u + 2 * 16u);
        EXPECT_TRUE(sms.modifier.getValueBuf().getLength() >= sms.modifier.getValueBuf().getPos());
    }
}

TEST("FieldSearchSpec construction") {
    {
        FieldSearchSpec f;
        EXPECT_FALSE(f.valid());
        EXPECT_EQUAL(0u, f.id());
        EXPECT_EQUAL("", f.name());
        EXPECT_EQUAL(0x100000u, f.maxLength());
        EXPECT_EQUAL("", f.arg1());
        EXPECT_TRUE(Normalizing::LOWERCASE_AND_FOLD == f.normalize_mode());
    }
    {
        FieldSearchSpec f(7, "f0", Searchmethod::AUTOUTF8, Normalizing::LOWERCASE, "substring", 789);
        EXPECT_TRUE(f.valid());
        EXPECT_EQUAL(7u, f.id());
        EXPECT_EQUAL("f0", f.name());
        EXPECT_EQUAL(789u, f.maxLength());
        EXPECT_EQUAL(789u, f.searcher().maxFieldLength());
        EXPECT_EQUAL("substring", f.arg1());
        EXPECT_TRUE(Normalizing::LOWERCASE == f.normalize_mode());
    }
}

TEST("FieldSearchSpec reconfiguration preserves match/normalization properties for new searcher") {
    FieldSearchSpec f(7, "f0", Searchmethod::AUTOUTF8, Normalizing::NONE, "substring", 789);
    QueryNodeResultFactory qnrf;
    QueryTerm qt(qnrf.create(), "foo", "index", TermType::EXACTSTRINGTERM, Normalizing::LOWERCASE_AND_FOLD);
    // Match type, normalization mode and max length are all properties of the original spec
    // and should be propagated to the new searcher.
    f.reconfig(qt);
    EXPECT_EQUAL(f.searcher().match_type(), FieldSearcher::MatchType::SUBSTRING);
    EXPECT_EQUAL(f.searcher().normalize_mode(), Normalizing::NONE);
    EXPECT_EQUAL(f.searcher().maxFieldLength(), 789u);
}

TEST("snippet modifier manager") {
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
            EXPECT_EQUAL(sm->getValueBuf().getLength(), 128u);
            EXPECT_EQUAL(searcher->getModifiedBuf().getLength(), 64u);
        }
        {
            auto * sm = static_cast<SnippetModifier *>(man.getModifiers().getModifier(1));
            UTF8SubstringSnippetModifier * searcher = sm->getSearcher().get();
            EXPECT_EQUAL(sm->getValueBuf().getLength(), 128u);
            EXPECT_EQUAL(searcher->getModifiedBuf().getLength(), 64u);
        }
    }
}

TEST("Stripping of indexes")
{
    EXPECT_EQUAL("f", FieldSearchSpecMap::stripNonFields("f"));
    EXPECT_EQUAL("f", FieldSearchSpecMap::stripNonFields("f[0]"));
    EXPECT_EQUAL("f[a]", FieldSearchSpecMap::stripNonFields("f[a]"));

    EXPECT_EQUAL("f.value", FieldSearchSpecMap::stripNonFields("f{a}"));
    EXPECT_EQUAL("f.value", FieldSearchSpecMap::stripNonFields("f{a0}"));
    EXPECT_EQUAL("f{a 0}", FieldSearchSpecMap::stripNonFields("f{a 0}"));
    EXPECT_EQUAL("f.value", FieldSearchSpecMap::stripNonFields("f{\"a 0\"}"));
}

TEST("counting of words") {
    EXPECT_TRUE(assertCountWords(0, ""));
    EXPECT_TRUE(assertCountWords(0, "?"));
    EXPECT_TRUE(assertCountWords(1, "foo"));
    EXPECT_TRUE(assertCountWords(2, "foo bar"));
    EXPECT_TRUE(assertCountWords(2, "? foo bar"));
    EXPECT_TRUE(assertCountWords(2, "foo bar ?"));

    // check that 'a' is counted as 1 word
    UTF8StrChrFieldSearcher fs(0);
    StringList field = StringList().add("a").add("aa bb cc");
    assertString(fs, "bb", field, Hits().add({1, 1}));
    assertString(fs, StringList().add("bb").add("not"), field, HitsList().add(Hits().add({1, 1})).add(Hits()));
}

TEST("element lengths")
{
    UTF8StrChrFieldSearcher fs(0);
    auto field = StringList().add("a").add("b a c").add("d a");
    auto query = StringList().add("a");
    auto qtv = performSearch(fs, query, getFieldValue(field));
    EXPECT_EQUAL(1u, qtv.size());
    auto& qt = *qtv[0];
    auto& hl = qt.getHitList();
    EXPECT_EQUAL(3u, hl.size());
    EXPECT_EQUAL(1u, hl[0].element_length());
    EXPECT_EQUAL(3u, hl[1].element_length());
    EXPECT_EQUAL(2u, hl[2].element_length());
}

vespalib::string NormalizationInput = "test That Somehing happens with during NårmØlization";

void
verifyNormalization(Normalizing normalizing, size_t expected_len, const char * expected) {
    ucs4_t buf[256];
    TokenizeReader reader(reinterpret_cast<const search::byte *>(NormalizationInput.c_str()), NormalizationInput.size(), buf);
    while (reader.hasNext()) {
        reader.normalize(reader.next(), normalizing);
    }
    size_t len = reader.complete();
    EXPECT_EQUAL(expected_len, len);
    EXPECT_EQUAL(0,  Fast_UnicodeUtil::utf8cmp(expected, buf));
}

TEST("test normalizing") {
    verifyNormalization(Normalizing::NONE, 52, NormalizationInput.c_str());
    verifyNormalization(Normalizing::LOWERCASE, 52, "test that somehing happens with during nårmølization");
    verifyNormalization(Normalizing::LOWERCASE_AND_FOLD, 54, "test that somehing happens with during naarmoelization");
}

TEST_MAIN() { TEST_RUN_ALL(); }
