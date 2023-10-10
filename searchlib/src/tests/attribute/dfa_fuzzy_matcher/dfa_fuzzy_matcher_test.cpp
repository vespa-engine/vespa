// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/dictionary_config.h>
#include <vespa/searchlib/attribute/dfa_fuzzy_matcher.h>
#include <vespa/searchlib/attribute/enumstore.h>
#include <vespa/searchlib/attribute/i_enum_store_dictionary.h>
#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/fuzzy/levenshtein_dfa.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/text/utf8.h>
#include <filesystem>
#include <fstream>
#include <iostream>

namespace fs = std::filesystem;

static std::string benchmark_dictionary;
static size_t dfa_words_to_match = 1000;
static size_t brute_force_words_to_match = 0;

bool benchmarking_enabled() {
    return !benchmark_dictionary.empty();
}

using namespace search::attribute;
using namespace search;
using vespalib::FuzzyMatcher;
using vespalib::datastore::AtomicEntryRef;
using vespalib::datastore::EntryRef;
using vespalib::fuzzy::LevenshteinDfa;
using vespalib::Utf8Reader;
using vespalib::Utf8Writer;

using StringEnumStore = EnumStoreT<const char*>;
using DictionaryEntry = std::pair<std::string, size_t>;
using RawDictionary = std::vector<DictionaryEntry>;
using StringVector = std::vector<std::string>;

namespace {

const char* char_from_u8(const char8_t* p) {
    return reinterpret_cast<const char*>(p);
}

}

RawDictionary
read_dictionary()
{
    RawDictionary result;
    std::ifstream file(benchmark_dictionary);
    if (!file.is_open()) {
        std::cerr << "Could not open benchmark dictionary file '" << benchmark_dictionary << "'" << std::endl;
        return result;
    }
    std::string line;
    std::string word;
    size_t freq;

    /**
     * Each line in the dictionary file should be on this format:
     * word\tfrequency\n
     *
     * This is the same format used when dumping a disk index dictionary using 'vespa-index-inspect dumpwords'.
     * See https://docs.vespa.ai/en/reference/vespa-cmdline-tools.html#vespa-index-inspect.
     */
    while (std::getline(file, line)) {
        std::istringstream iss(line);
        if (std::getline(iss, word, '\t') && iss >> freq) {
            result.emplace_back(word, freq);
        } else {
            std::cerr << "Invalid line: '" << line << "'" << std::endl;
        }
    }
    file.close();
    return result;
}

StringVector
to_string_vector(const RawDictionary& dict)
{
    StringVector result;
    for (const auto& entry : dict) {
        result.push_back(entry.first);
    }
    return result;
}

void
sort_by_freq(RawDictionary& dict)
{
    std::sort(dict.begin(), dict.end(),
              [](const DictionaryEntry& lhs, const DictionaryEntry& rhs) {
        return lhs.second > rhs.second;
    });
}

struct MatchStats {
    size_t matches;
    size_t seeks;
    vespalib::duration elapsed;
    size_t samples;
    MatchStats() : matches(0), seeks(0), elapsed(0), samples(0) {}
    void add_sample(size_t matches_in, size_t seeks_in, vespalib::duration elapsed_in) {
        matches += matches_in;
        seeks += seeks_in;
        elapsed += elapsed_in;
        ++samples;
    }
    double avg_matches() const {
        return (double) matches / samples;
    }
    double avg_seeks() const {
        return (double) seeks / samples;
    }
    double avg_elapsed_ms() const {
        return (double) vespalib::count_ms(elapsed) / samples;
    }
};

template <bool collect_matches>
void
brute_force_fuzzy_match_in_dictionary(std::string_view target, const StringEnumStore& store, uint32_t prefix_size, bool cased, MatchStats& stats, StringVector& matched_words)
{
    auto view = store.get_dictionary().get_posting_dictionary().getFrozenView();
    vespalib::Timer timer;
    FuzzyMatcher matcher(target, 2, prefix_size, cased);
    auto itr = view.begin();
    size_t matches = 0;
    size_t seeks = 0;
    while (itr.valid()) {
        auto word = store.get_value(itr.getKey().load_relaxed());
        if (matcher.isMatch(word)) {
            ++matches;
            if (collect_matches) {
                matched_words.push_back(word);
            }
        }
        ++seeks;
        ++itr;
    }
    stats.add_sample(matches, seeks, timer.elapsed());
}

template <bool collect_matches>
void
dfa_fuzzy_match_in_dictionary(std::string_view target, const StringEnumStore& store, uint32_t prefix_size, bool cased, MatchStats& stats, StringVector& matched_words)
{
    auto view = store.get_dictionary().get_posting_dictionary().getFrozenView();
    vespalib::Timer timer;
    DfaFuzzyMatcher matcher(target, 2, prefix_size, cased, LevenshteinDfa::DfaType::Explicit);
    Utf8Reader reader(vespalib::stringref(target.data(), target.size()));
    std::string target_copy;
    Utf8Writer<std::string> writer(target_copy);
    for (size_t pos = 0; pos < prefix_size && reader.hasMore(); ++pos) {
        auto code_point = reader.getChar();
        writer.putChar(code_point);
    }
    auto prefix_cmp = store.make_folded_comparator_prefix(target_copy.c_str());
    auto itr = prefix_size > 0 ? view.lowerBound(AtomicEntryRef(), prefix_cmp) : view.begin();
    auto itr_end = itr;
    if (itr_end.valid()) {
        if (prefix_size > 0) {
            if (!prefix_cmp.less(EntryRef(), itr_end.getKey().load_relaxed())) {
                itr_end.seekPast(AtomicEntryRef(), prefix_cmp);
            }
        } else {
            itr_end.end();
        }
    }
    size_t matches = 0;
    size_t seeks = 0;
    while (itr != itr_end) {
        auto word = store.get_value(itr.getKey().load_relaxed());
        if (matcher.is_match(word, itr, store.get_data_store())) {
            ++itr;
            ++matches;
            if (collect_matches) {
                matched_words.push_back(word);
            }
        } else {
            ++seeks;
        }
    }
    stats.add_sample(matches, seeks, timer.elapsed());
}

template <bool collect_matches>
void
dfa_fuzzy_match_in_dictionary_no_skip(std::string_view target, const StringEnumStore& store, uint32_t prefix_size, bool cased, MatchStats& stats, StringVector& matched_words)
{
    auto view = store.get_dictionary().get_posting_dictionary().getFrozenView();
    vespalib::Timer timer;
    DfaFuzzyMatcher matcher(target, 2, prefix_size, cased, LevenshteinDfa::DfaType::Explicit);
    auto itr = view.begin();
    size_t matches = 0;
    size_t seeks = 0;
    for (;itr.valid(); ++itr) {
        auto word = store.get_value(itr.getKey().load_relaxed());
        if (matcher.is_match(word)) {
            ++matches;
            if (collect_matches) {
                matched_words.push_back(word);
            }
        } else {
            ++seeks;
        }
    }
    stats.add_sample(matches, seeks, timer.elapsed());
}

struct TestParam
{
    vespalib::string _name;
    bool             _cased;

    TestParam(vespalib::string name, bool cased)
        : _name(std::move(name)),
          _cased(cased)
    {
    }
    TestParam(const TestParam&);
    ~TestParam();
};

TestParam::TestParam(const TestParam&) = default;

TestParam::~TestParam() = default;

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << param._name;
    return os;
}

struct DfaFuzzyMatcherTest : public ::testing::TestWithParam<TestParam> {
    StringEnumStore store;
    DfaFuzzyMatcherTest()
        : store(true, DictionaryConfig(DictionaryConfig::Type::BTREE, GetParam()._cased ? DictionaryConfig::Match::CASED : DictionaryConfig::Match::UNCASED))
    {}
    void populate_dictionary(const StringVector& words) {
        auto updater = store.make_batch_updater();
        for (const auto& word : words) {
            auto ref = updater.insert(word.c_str());
            updater.inc_ref_count(ref);
        }
        updater.commit();
        store.freeze_dictionary();
    }
    void expect_prefix_matches(std::string_view target, uint32_t prefix_size, const StringVector& exp_matches) {
        MatchStats stats;
        StringVector brute_force_matches;
        StringVector dfa_matches;
        StringVector dfa_no_skip_matches;
        bool cased = GetParam()._cased;
        SCOPED_TRACE(target);
        brute_force_fuzzy_match_in_dictionary<true>(target, store, prefix_size, cased, stats, brute_force_matches);
        dfa_fuzzy_match_in_dictionary<true>(target, store, prefix_size, cased, stats, dfa_matches);
        dfa_fuzzy_match_in_dictionary_no_skip<true>(target, store, prefix_size, cased, stats, dfa_no_skip_matches);
        EXPECT_EQ(exp_matches, brute_force_matches);
        EXPECT_EQ(exp_matches, dfa_matches);
        EXPECT_EQ(exp_matches, dfa_no_skip_matches);
    }
    void expect_matches(std::string_view target, const StringVector& exp_matches) {
        expect_prefix_matches(target, 0, exp_matches);
    }
};

INSTANTIATE_TEST_SUITE_P(DfaFuzzyMatcherMultiTest,
                         DfaFuzzyMatcherTest,
                         testing::Values(TestParam("uncased", false), TestParam("cased", true)),
                         testing::PrintToStringParamName());

TEST_P(DfaFuzzyMatcherTest, fuzzy_match_in_dictionary)
{
    StringVector words = { "board", "boat", "bob", "door", "food", "foot", "football", "foothill",
                           "for", "forbid", "force", "ford", "forearm", "forecast", "forest" };
    populate_dictionary(words);
    expect_matches("board", {"board", "boat", "ford"});
    expect_matches("food", {"door", "food", "foot", "for", "ford"});
    expect_matches("foothill", {"football", "foothill"});
    expect_matches("for", {"bob", "door", "food", "foot", "for", "force", "ford"});
    expect_matches("force", {"for", "force", "ford"});
    expect_matches("forcecast", {"forecast"});
}

TEST_P(DfaFuzzyMatcherTest, fuzzy_match_in_dictionary_with_prefix_size)
{
    bool cased = GetParam()._cased;
    StringVector words = { "board", "boat", "bob", "door", "food", "foot", "football", "foothill",
                           "for", "forbid", "force", "ford", "forearm", "forecast", "forest", "H", "HA", "h", "ha", char_from_u8(u8"Ørn"), char_from_u8(u8"øre"), char_from_u8(u8"Ås"), char_from_u8(u8"ås")};
    populate_dictionary(words);
    expect_prefix_matches("a", 1, {});
    expect_prefix_matches("b", 1, {"bob"});
    expect_prefix_matches("board", 1, {"board", "boat"});
    expect_prefix_matches("c", 1, {});
    expect_prefix_matches("food", 1, {"food", "foot", "for", "ford"});
    expect_prefix_matches("food", 2, {"food", "foot", "for", "ford"});
    expect_prefix_matches("food", 3, {"food", "foot"});
    expect_prefix_matches("foothill", 1, {"football", "foothill"});
    expect_prefix_matches("for", 1, {"food", "foot", "for", "force", "ford"});
    expect_prefix_matches("for", 2, {"food", "foot", "for", "force", "ford"});
    expect_prefix_matches("for", 3, {"for", "force", "ford"});
    expect_prefix_matches("force", 1, {"for", "force", "ford"});
    expect_prefix_matches("forcecast", 1, {"forecast"});
    expect_prefix_matches("forcecast", 4, {});
    expect_prefix_matches("z", 1, {});
    if (cased) {
        expect_prefix_matches("h", 1, {"h", "ha"});
        expect_prefix_matches(char_from_u8(u8"Ø"), 1, {char_from_u8(u8"Ørn")});
        expect_prefix_matches(char_from_u8(u8"ø"), 1, {char_from_u8(u8"øre")});
        expect_prefix_matches(char_from_u8(u8"å"), 1, {char_from_u8(u8"ås")});
        /* Corner case: prefix length > target length means exact match */
        expect_prefix_matches("h", 2, {"h"});
    } else {
        expect_prefix_matches("h", 1, {"H", "h", "HA", "ha"});
        expect_prefix_matches(char_from_u8(u8"ø"), 1, {char_from_u8(u8"øre"), char_from_u8(u8"Ørn")});
        expect_prefix_matches(char_from_u8(u8"å"), 1, {char_from_u8(u8"Ås"), char_from_u8(u8"ås")});
        /* Corner case: prefix length > target length means exact match */
        expect_prefix_matches("h", 2, {"H", "h"});
    }
}

void
benchmark_fuzzy_match_in_dictionary(const StringEnumStore& store, const RawDictionary& dict, size_t words_to_match, bool cased, bool dfa_algorithm)
{
    MatchStats stats;
    StringVector dummy;
    for (size_t i = 0; i < std::min(words_to_match, dict.size()); ++i) {
        const auto& entry = dict[i];
        if (dfa_algorithm) {
            dfa_fuzzy_match_in_dictionary<false>(entry.first, store, 0, cased, stats, dummy);
        } else {
            brute_force_fuzzy_match_in_dictionary<false>(entry.first, store, 0, cased, stats, dummy);
        }
    }
    std::cout << (dfa_algorithm ? "DFA:" : "Brute force:") << " samples=" << stats.samples << ", avg_matches=" << stats.avg_matches() << ", avg_seeks=" << stats.avg_seeks() << ", avg_elapsed_ms=" << stats.avg_elapsed_ms() << std::endl;
}

using DfaFuzzyMatcherBenchmarkTest = DfaFuzzyMatcherTest;

INSTANTIATE_TEST_SUITE_P(DfaFuzzyMatcherBenchmarkMultiTest,
                         DfaFuzzyMatcherBenchmarkTest,
                         testing::Values(TestParam("uncased", false)),
                         testing::PrintToStringParamName());

TEST_P(DfaFuzzyMatcherBenchmarkTest, benchmark_fuzzy_match_in_dictionary)
{
    if (!benchmarking_enabled()) {
        GTEST_SKIP() << "benchmarking not enabled";
    }
    auto dict = read_dictionary();
    populate_dictionary(to_string_vector(dict));
    std::cout << "Unique words: " << store.get_num_uniques() << std::endl;
    sort_by_freq(dict);
    bool cased = GetParam()._cased;
    benchmark_fuzzy_match_in_dictionary(store, dict, dfa_words_to_match, cased, true);
    benchmark_fuzzy_match_in_dictionary(store, dict, brute_force_words_to_match, cased, false);
}

int
main(int argc, char** argv)
{
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 1) {
        benchmark_dictionary = argv[1];
        if (!fs::exists(fs::path(benchmark_dictionary))) {
            std::cerr << "Benchmark dictionary file '" << benchmark_dictionary << "' does not exist" << std::endl;
            return 1;
        }
        if (argc > 2) {
            dfa_words_to_match = std::stoi(argv[2]);
        }
        if (argc > 3) {
            brute_force_words_to_match = std::stoi(argv[3]);
        }
    }
    return RUN_ALL_TESTS();
}

