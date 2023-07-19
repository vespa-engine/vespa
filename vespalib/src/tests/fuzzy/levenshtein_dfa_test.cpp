// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/fuzzy/levenshtein_dfa.h>
#include <vespa/vespalib/fuzzy/dfa_stepping_base.h>
#include <vespa/vespalib/fuzzy/unicode_utils.h>
#include <vespa/vespalib/fuzzy/levenshtein_distance.h> // For benchmarking purposes
#include <vespa/vespalib/util/benchmark_timer.h>
#include <charconv>
#include <concepts>
#include <filesystem>
#include <fstream>
#include <string>
#include <string_view>
#include <gtest/gtest.h>

using namespace ::testing;
using namespace vespalib::fuzzy;
namespace fs = std::filesystem;

static std::string benchmark_dictionary;

struct LevenshteinDfaTest : TestWithParam<LevenshteinDfa::DfaType> {

    static LevenshteinDfa::DfaType dfa_type() noexcept { return GetParam(); }

    static std::optional<uint32_t> calculate(std::string_view left, std::string_view right, uint32_t threshold) {
        auto dfa_lhs = LevenshteinDfa::build(left, threshold, dfa_type());
        auto maybe_match_lhs = dfa_lhs.match(right, nullptr);

        auto dfa_rhs = LevenshteinDfa::build(right, threshold, dfa_type());
        auto maybe_match_rhs = dfa_rhs.match(left, nullptr);

        EXPECT_EQ(maybe_match_lhs.matches(), maybe_match_rhs.matches());
        if (maybe_match_lhs.matches()) {
            EXPECT_EQ(maybe_match_lhs.edits(), maybe_match_rhs.edits());
            return {maybe_match_lhs.edits()};
        }
        return std::nullopt;
    }

    static std::optional<uint32_t> calculate(std::u8string_view left, std::u8string_view right, uint32_t threshold) {
        std::string_view lhs_ch(reinterpret_cast<const char*>(left.data()), left.size());
        std::string_view rhs_ch(reinterpret_cast<const char*>(right.data()), right.size());
        return calculate(lhs_ch, rhs_ch, threshold);
    }

};

INSTANTIATE_TEST_SUITE_P(AllDfaTypes,
                         LevenshteinDfaTest,
                         Values(LevenshteinDfa::DfaType::Explicit,
                                LevenshteinDfa::DfaType::Implicit),
                         PrintToStringParamName());

// Same as existing non-DFA Levenshtein tests, but with some added instantiations
// for smaller max distances.
TEST_P(LevenshteinDfaTest, edge_cases_have_correct_edit_distance) {
    EXPECT_EQ(calculate("abc", "abc", 2),  std::optional{0});
    for (auto max : {1, 2}) {
        EXPECT_EQ(calculate("abc", "ab1", max),  std::optional{1}) << max;
        EXPECT_EQ(calculate("abc", "1bc", max),  std::optional{1}) << max;
        EXPECT_EQ(calculate("abc", "a1c", max),  std::optional{1}) << max;
        EXPECT_EQ(calculate("abc", "ab", max),   std::optional{1}) << max;
        EXPECT_EQ(calculate("abc", "abcd", max), std::optional{1}) << max;
        EXPECT_EQ(calculate("a", "", max),       std::optional{1}) << max;
    }
    EXPECT_EQ(calculate("bc", "abcd", 2),  std::optional{2});
    EXPECT_EQ(calculate("ab", "abcd", 2),  std::optional{2});
    EXPECT_EQ(calculate("cd", "abcd", 2),  std::optional{2});
    EXPECT_EQ(calculate("ad", "abcd", 2),  std::optional{2});
    EXPECT_EQ(calculate("abc", "a12", 2),  std::optional{2});
    EXPECT_EQ(calculate("abc", "123", 2),  std::nullopt);
    EXPECT_EQ(calculate("ab", "", 1),      std::nullopt);
    EXPECT_EQ(calculate("ab", "", 2),      std::optional{2});
    EXPECT_EQ(calculate("abc", "", 2),     std::nullopt);
    EXPECT_EQ(calculate("abc", "123", 2),  std::nullopt);
}

TEST_P(LevenshteinDfaTest, distance_is_in_utf32_code_point_space) {
    // Each hiragana/katakana/kanji corresponds to multiple (3) UTF-8 chars but a single UTF-32 code point.
    EXPECT_EQ(calculate(u8"猫", u8"猫", 2), std::optional{0});
    EXPECT_EQ(calculate(u8"猫", u8"犬", 2), std::optional{1});
    EXPECT_EQ(calculate(u8"猫と犬", u8"犬と猫", 2), std::optional{2});
    EXPECT_EQ(calculate(u8"猫は好き", u8"犬が好き", 2), std::optional{2});
    EXPECT_EQ(calculate(u8"カラオケ", u8"カラオケ", 2), std::optional{0});
    EXPECT_EQ(calculate(u8"カラオケ", u8"カラoケ", 2), std::optional{1});
    EXPECT_EQ(calculate(u8"カラオケ", u8"カraオケ", 2), std::optional{2});
    EXPECT_EQ(calculate(u8"kaラオケ", u8"カラオケ", 2), std::optional{2});
    EXPECT_EQ(calculate(u8"カラオケ", u8"カラoke", 2), std::nullopt);
}

void test_dfa_successor(const LevenshteinDfa& dfa, std::string_view source, std::string_view expected_successor) {
    std::string successor;
    auto m = dfa.match(source, &successor);
    if (m.matches()) {
        FAIL() << "Expected '" << source << "' to emit a successor, but it "
               << "matched with " << static_cast<uint32_t>(m.edits())
               << " edits (of max " << static_cast<uint32_t>(m.max_edits()) <<  " edits)";
    }
    EXPECT_EQ(successor, expected_successor);
    EXPECT_TRUE(dfa.match(successor, nullptr).matches());
}

TEST_P(LevenshteinDfaTest, can_generate_successors_to_mismatching_source_strings) {
    auto dfa = LevenshteinDfa::build("food", 1, dfa_type());

    test_dfa_successor(dfa, "",       "\x01""food");
    test_dfa_successor(dfa, "faa",    "faod");
    test_dfa_successor(dfa, "fooooo", "foop");
    test_dfa_successor(dfa, "ooof",   "pfood");
    test_dfa_successor(dfa, "fo",     "fo\x01""d");
    test_dfa_successor(dfa, "oo",     "ood");
    test_dfa_successor(dfa, "ooo",    "oood");
    test_dfa_successor(dfa, "foh",    "fohd");
    test_dfa_successor(dfa, "foho",   "fohod");
    test_dfa_successor(dfa, "foxx",   "foyd");
    test_dfa_successor(dfa, "xfa",    "xfood");
    test_dfa_successor(dfa, "gg",     "good");
    test_dfa_successor(dfa, "gp",     "hfood");
    test_dfa_successor(dfa, "ep",     "f\x01""od");
    test_dfa_successor(dfa, "hfoodz", "hood");
    test_dfa_successor(dfa, "aooodz", "bfood");

    // Also works with Unicode
    // 2 chars
    test_dfa_successor(dfa, "\xc3\x86""x",                // "Æx"
                            "\xc3\x87""food");            // "Çfood"
    // 3 chars
    test_dfa_successor(dfa, "\xe7\x8c\xab""\xe3\x81\xaf", // "猫は"
                            "\xe7\x8c\xac""food");        // "猬food"
    // 4 chars
    test_dfa_successor(dfa, "\xf0\x9f\xa4\xa9""abc",      // <starry eyed emoji>abc
                            "\xf0\x9f\xa4\xa9""food");    // <starry eyed emoji>food

    // Note that as a general rule, emojis are fickle beasts to deal with since a single
    // emoji often takes up multiple code points, which we consider separate characters
    // but a user sees as a single actual rendered glyph.
    // Multi-code point character edit distance support is left as an exercise for the reader :D
}

TEST_P(LevenshteinDfaTest, successor_is_well_defined_for_max_unicode_code_point_input) {
    auto dfa = LevenshteinDfa::build("food", 1, dfa_type());
    // The successor string must be lexicographically larger than the input string.
    // In the presence of a wildcard output edge we handle this by increase the input
    // character by 1 and encoding it back as UTF-8.
    // It is possible (though arguably very unlikely) that the input character is
    // U+10FFFF, which is the maximum valid Unicode character. We have to ensure that
    // we can encode U+10FFFF + 1, even though it's technically outside the valid range.
    // Luckily, UTF-8 can technically (there's that word again) encode up to U+1FFFFF,
    // so the resulting string is byte-wise greater, and that's what matters since we
    // don't guarantee that the successor string is _valid_ UTF-8.
    // This problem does not happen with the target string, as it's an invalid character
    // and will be replaced with the Unicode replacement char before we ever see it.
    test_dfa_successor(dfa, "\xf4\x8f\xbf\xbf""xyz", // U+10FFFF
                            "\xf4\x90\x80\x80""food");// U+10FFFF+1
}

TEST_P(LevenshteinDfaTest, successor_is_well_defined_for_empty_target) {
    auto dfa = LevenshteinDfa::build("", 1, dfa_type());
    test_dfa_successor(dfa, "aa",    "b");
    test_dfa_successor(dfa, "b\x01", "c");
    test_dfa_successor(dfa, "vespa", "w");
}

// We should normally be able to rely on higher-level components to ensure we
// only receive valid UTF-8, but make sure we don't choke on it if we do get it.
TEST_P(LevenshteinDfaTest, malformed_utf8_is_replaced_with_placeholder_char) {
    // 0xff is not a valid encoding and is implicitly converted to U+FFFD,
    // which is the standard Unicode replacement character.
    EXPECT_EQ(calculate("\xff", "a", 2),         std::optional{1});
    EXPECT_EQ(calculate("\xff\xff", "a", 2),     std::optional{2});
    EXPECT_EQ(calculate("a", "\xff", 2),         std::optional{1});
    EXPECT_EQ(calculate("a", "\xff\xff\xff", 2), std::nullopt);
    EXPECT_EQ(calculate("\xff", "\xef\xbf\xbd"/*U+FFFD*/, 2),  std::optional{0});
}

TEST_P(LevenshteinDfaTest, unsupported_max_edits_value_throws) {
    EXPECT_THROW((void)LevenshteinDfa::build("abc", 0, dfa_type()), std::invalid_argument);
    EXPECT_THROW((void)LevenshteinDfa::build("abc", 3, dfa_type()), std::invalid_argument);
}

// Turn integer v into its bitwise string representation with the MSB as the leftmost character.
template <std::unsigned_integral T>
std::string bits_to_str(T v) {
    constexpr const uint8_t n_bits = sizeof(T) * 8;
    std::string ret(n_bits, '0');
    for (uint8_t bit = 0; bit < n_bits; ++bit) {
        if (v & (1 << bit)) {
            ret[n_bits - bit - 1] = '1';
        }
    }
    return ret;
}

using DfaTypeAndMaxEdits = std::tuple<LevenshteinDfa::DfaType, uint32_t>;

struct LevenshteinDfaSuccessorTest : TestWithParam<DfaTypeAndMaxEdits> {
    // Print test suffix as e.g. "/Explicit_1" instead of just a GTest-chosen number.
    static std::string stringify_params(const TestParamInfo<ParamType>& info) {
        std::ostringstream ss;
        ss << std::get<0>(info.param) << '_' << std::get<1>(info.param);
        return ss.str();
    }
};

INSTANTIATE_TEST_SUITE_P(SupportedMaxEdits,
                         LevenshteinDfaSuccessorTest,
                         Combine(Values(LevenshteinDfa::DfaType::Explicit,
                                        LevenshteinDfa::DfaType::Implicit),
                                 Values(1, 2)),
                         LevenshteinDfaSuccessorTest::stringify_params);

/**
 * Exhaustively test successor generation by matching all target and source strings
 * in {0,1}^8 against each other. Since we generate bit strings identical to the
 * bit patterns of the underlying counter(s), any string at index `i+1` will compare
 * lexicographically greater than the one at `i`. We use this to test that we never
 * miss a valid match that comes between a mismatch and its generated successor.
 *
 * For each mismatch we note the successor it emitted. Verify that each subsequent
 * match() invocation for a source string < the successor results in a mismatch.
 *
 * We test this for both max edit distance 1 and 2. Despite being an exhaustive test,
 * this completes in a few dozen milliseconds even with ASan instrumentation.
 *
 * Inspired by approach used by Lucene DFA exhaustive testing.
 */
TEST_P(LevenshteinDfaSuccessorTest, exhaustive_successor_test) {
    const auto [dfa_type, max_edits] = GetParam();
    for (uint32_t i = 0; i < 256; ++i) {
        const auto target = bits_to_str(static_cast<uint8_t>(i));
        auto target_dfa = LevenshteinDfa::build(target, max_edits, dfa_type);
        std::string skip_to, successor;
        for (uint32_t j = 0; j < 256; ++j) {
            const auto source = bits_to_str(static_cast<uint8_t>(j));
            auto maybe_match = target_dfa.match(source, &successor);
            if (maybe_match.matches() && !skip_to.empty()) {
                ASSERT_GE(source, skip_to);
            } else if (!maybe_match.matches()) {
                ASSERT_FALSE(successor.empty()) << source;
                ASSERT_GE(successor, skip_to)   << source;
                ASSERT_GT(successor, source)    << source;
                skip_to = successor;
            }
        }
    }
}

namespace {

template <uint8_t MaxEdits>
void explore(const DfaSteppingBase<FixedMaxEditDistanceTraits<MaxEdits>>& stepper,
             const typename DfaSteppingBase<FixedMaxEditDistanceTraits<MaxEdits>>::StateType& in_state)
{
    ASSERT_EQ(stepper.can_match(stepper.step(in_state, WILDCARD)),
              stepper.can_wildcard_step(in_state));
    if (!stepper.can_match(in_state)) {
        return; // reached the end of the line
    }
    // DFS-explore all matching transitions, as well as one non-matching transition
    auto t = stepper.transitions(in_state);
    for (uint32_t c: t.u32_chars()) {
        ASSERT_NO_FATAL_FAILURE(explore(stepper, stepper.step(in_state, c)));
    }
    ASSERT_NO_FATAL_FAILURE(explore(stepper, stepper.step(in_state, WILDCARD)));
}

} // anon ns

using StateStepperTypes = Types<
    DfaSteppingBase<FixedMaxEditDistanceTraits<1>>,
    DfaSteppingBase<FixedMaxEditDistanceTraits<2>>
>;

template <typename SteppingBase>
struct LevenshteinSparseStateTest : Test {};

TYPED_TEST_SUITE(LevenshteinSparseStateTest, StateStepperTypes);

// "Meta-test" for checking that the `can_wildcard_step` predicate function is
// functionally equivalent to evaluating `can_match(stepper.step(in_state, WILDCARD))`
TYPED_TEST(LevenshteinSparseStateTest, wildcard_step_predcate_is_equivalent_to_step_with_can_match) {
    for (const char* target : {"", "a", "ab", "abc", "abcdef", "aaaaa"}) {
        auto u32_target = utf8_string_to_utf32(target);
        TypeParam stepper(u32_target);
        ASSERT_NO_FATAL_FAILURE(explore(stepper, stepper.start()));
    }
}

template <typename T>
void do_not_optimize_away(T&& t) noexcept {
    asm volatile("" : : "m"(t) : "memory"); // Clobber the value to avoid losing it to compiler optimizations
}

enum class BenchmarkType {
    DfaExplicit,
    DfaImplicit,
    Legacy
};

const char* to_s(BenchmarkType t) noexcept {
    // Note: need underscores since this is used as part of GTest-generated test instance names
    switch (t) {
    case BenchmarkType::DfaExplicit: return "DFA_explicit";
    case BenchmarkType::DfaImplicit: return "DFA_implicit";
    case BenchmarkType::Legacy:      return "legacy";
    }
    abort();
}

[[nodiscard]] bool benchmarking_enabled() noexcept {
    return !benchmark_dictionary.empty();
}

[[nodiscard]] std::vector<uint32_t> string_lengths() {
    return {2, 8, 16, 64, 256, 1024, 1024*16, 1024*64};
}

struct LevenshteinBenchmarkTest : TestWithParam<BenchmarkType> {

    static std::string stringify_params(const TestParamInfo<ParamType>& info) {
        return to_s(info.param);
    }

    void SetUp() override {
        if (!benchmarking_enabled()) {
            GTEST_SKIP() << "benchmarking not enabled";
        }
    }

    static BenchmarkType benchmark_type() noexcept { return GetParam(); }

    static const std::vector<std::string>& load_dictionary_once() {
        static auto sorted_lines = read_and_sort_all_lines(fs::path(benchmark_dictionary));
        return sorted_lines;
    }

    static std::vector<std::string> read_and_sort_all_lines(const fs::path& file_path) {
        std::ifstream ifs(file_path);
        if (!ifs.is_open()) {
            throw std::invalid_argument("File does not exist");
        }
        std::vector<std::string> lines;
        std::string line;
        while (std::getline(ifs, line)) {
            lines.emplace_back(line);
        }
        std::sort(lines.begin(), lines.end());
        return lines;
    }
};

INSTANTIATE_TEST_SUITE_P(AllDfaTypes,
                         LevenshteinBenchmarkTest,
                         Values(BenchmarkType::DfaExplicit,
                                BenchmarkType::DfaImplicit,
                                BenchmarkType::Legacy),
                         LevenshteinBenchmarkTest::stringify_params);

// ("abc", 1) => "a"
// ("abc", 3) => "abc"
// ("abc", 7) => "abcabca"
//  ... and so on.
std::string repeated_string(std::string_view str, uint32_t sz) {
    uint32_t chunks = sz / str.size();
    std::string ret;
    ret.reserve(sz);
    for (uint32_t i = 0; i < chunks; ++i) {
        ret += str;
    }
    uint32_t rem = sz % str.size();
    ret += str.substr(0, rem);
    return ret;
}

TEST_P(LevenshteinBenchmarkTest, benchmark_worst_case_matching_excluding_setup_time) {
    using vespalib::BenchmarkTimer;
    const auto type = benchmark_type();
    fprintf(stderr, "------ %s ------\n", to_s(type));
    for (uint8_t k : {1, 2}) {
        for (uint32_t sz : string_lengths()) {
            // Use same string as both source and target. This is the worst case in that the entire
            // string must be matched and any sparse representation is always maximally filled since
            // we never expend any edits via mismatches.
            // Also ensure that we have multiple out-edges per node (i.e. don't just repeat "AAA" etc.).
            std::string str = repeated_string("abcde", sz);
            double min_time_s;
            if (type == BenchmarkType::DfaExplicit || type == BenchmarkType::DfaImplicit) {
                auto dfa_type = (type == BenchmarkType::DfaExplicit) ? LevenshteinDfa::DfaType::Explicit
                                                                     : LevenshteinDfa::DfaType::Implicit;
                auto dfa = LevenshteinDfa::build(str, k, dfa_type);
                min_time_s = BenchmarkTimer::benchmark([&] {
                    auto res = dfa.match(str, nullptr); // not benchmarking successor generation
                    do_not_optimize_away(res);
                }, 1.0);
            } else {
                min_time_s = BenchmarkTimer::benchmark([&] {
                    auto str_u32 = utf8_string_to_utf32(str); // Must be done per term, so included in benchmark body
                    auto res = vespalib::LevenshteinDistance::calculate(str_u32, str_u32, k);
                    do_not_optimize_away(res);
                }, 1.0);
            }
            fprintf(stderr, "k=%u, sz=%u: \t%g us\n", k, sz, min_time_s * 1000000.0);
        }
    }
}

TEST(LevenshteinExplicitDfaBenchmarkTest, benchmark_explicit_dfa_construction) {
    if (!benchmarking_enabled()) {
        GTEST_SKIP() << "benchmarking not enabled";
    }
    using vespalib::BenchmarkTimer;
    for (uint8_t k : {1, 2}) {
        for (uint32_t sz : string_lengths()) {
            std::string str = repeated_string("abcde", sz);
            double min_time_s = BenchmarkTimer::benchmark([&] {
                auto dfa = LevenshteinDfa::build(str, k, LevenshteinDfa::DfaType::Explicit);
                do_not_optimize_away(dfa);
            }, 2.0);
            auto dfa = LevenshteinDfa::build(str, k, LevenshteinDfa::DfaType::Explicit);
            size_t mem_usage = dfa.memory_usage();
            fprintf(stderr, "k=%u, sz=%u: \t%g us \t%zu bytes\n", k, sz, min_time_s * 1000000.0, mem_usage);
        }
    }
}

TEST_P(LevenshteinBenchmarkTest, benchmark_brute_force_dictionary_scan) {
    using vespalib::BenchmarkTimer;
    const auto type = benchmark_type();
    const auto dict = load_dictionary_once();
    std::vector target_lengths = {1, 2, 4, 8, 12, 16, 24, 32, 64};
    fprintf(stderr, "------ %s ------\n", to_s(type));
    for (uint8_t k : {1, 2}) {
        for (uint32_t sz : target_lengths) {
            std::string str = repeated_string("abcde", sz);
            double min_time_s;
            if (type == BenchmarkType::DfaExplicit || type == BenchmarkType::DfaImplicit) {
                auto dfa_type = (type == BenchmarkType::DfaExplicit) ? LevenshteinDfa::DfaType::Explicit
                                                                     : LevenshteinDfa::DfaType::Implicit;
                auto dfa = LevenshteinDfa::build(str, k, dfa_type);
                min_time_s = BenchmarkTimer::benchmark([&] {
                    for (const auto& line : dict) {
                        auto res = dfa.match(line, nullptr);
                        do_not_optimize_away(res);
                    }
                }, 2.0);
            } else {
                min_time_s = BenchmarkTimer::benchmark([&] {
                    auto target_u32 = utf8_string_to_utf32(str);
                    for (const auto& line : dict) {
                        auto line_u32 = utf8_string_to_utf32(line);
                        auto res = vespalib::LevenshteinDistance::calculate(line_u32, target_u32, k);
                        do_not_optimize_away(res);
                    }
                }, 2.0);
            }
            fprintf(stderr, "k=%u, sz=%u: \t%g us\n", k, sz, min_time_s * 1000000.0);
        }
    }
}

TEST_P(LevenshteinBenchmarkTest, benchmark_skipping_dictionary_scan) {
    const auto type = benchmark_type();
    if (type == BenchmarkType::Legacy) {
        GTEST_SKIP() << "Skipping not supported for legacy implementation";
    }
    using vespalib::BenchmarkTimer;
    const auto dict = load_dictionary_once();
    std::vector target_lengths = {1, 2, 4, 8, 12, 16, 24, 32, 64};
    fprintf(stderr, "------ %s ------\n", to_s(type));
    for (uint8_t k : {1, 2}) {
        for (uint32_t sz : target_lengths) {
            std::string str = repeated_string("abcde", sz);
            auto dfa_type = (type == BenchmarkType::DfaExplicit) ? LevenshteinDfa::DfaType::Explicit
                                                                 : LevenshteinDfa::DfaType::Implicit;
            auto dfa = LevenshteinDfa::build(str, k, dfa_type);
            double min_time_s = BenchmarkTimer::benchmark([&] {
                auto iter = dict.cbegin();
                auto end = dict.cend();
                std::string successor;
                while (iter != end) {
                    auto maybe_match = dfa.match(*iter, &successor);
                    if (maybe_match.matches()) {
                        ++iter;
                    } else {
                        iter = std::lower_bound(iter, end, successor);
                    }
                }
            }, 2.0);
            fprintf(stderr, "k=%u, sz=%u: \t%g us\n", k, sz, min_time_s * 1000000.0);
        }
    }
}

// TODO:
//  - explicit successor generation benchmark

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    if (argc > 1) {
        benchmark_dictionary = argv[1];
        if (!fs::exists(fs::path(benchmark_dictionary))) {
            fprintf(stderr, "Benchmark dictionary file '%s' does not exist\n", benchmark_dictionary.c_str());
            return 1;
        }
    }
    return RUN_ALL_TESTS();
}
