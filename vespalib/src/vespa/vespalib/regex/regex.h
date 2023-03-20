// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>
#include <string_view>
#include <utility>

namespace vespalib {

/**
 * A simple Regex library wrapper which provides for both just-in-time
 * pattern evaluation as well as pattern precompilation and reuse.
 *
 * Robustness and input safety:
 * The underlying regex engine implementation must ensure that pattern
 * parsing and input processing is safe to be run on _untrusted_ inputs.
 * This means the underlying implementation shall provide upper bounds
 * on both memory and CPU time and may never crash or corrupt the process.
 *
 * We currently use Google RE2 under the hood to achieve this.
 *
 * Note: due to underlying RE2 limitations, string lengths may
 * not be longer than INT_MAX.
 *
 * Thread safety:
 * A Regex object is safe to be used from multiple threads.
 *
 * Exception safety:
 * Exceptions shall never be thrown from the regex code itself, neither
 * at parse time nor at match time (ancillary exceptions _could_ be thrown
 * from memory allocation failures etc, but we assume that the caller
 * is running vespamalloc which terminates the process instead, making
 * the whole thing effectively noexcept).
 *
 * If the provided regular expression pattern is malformed, parsing
 * fails silently; all match functions will return false immediately.
 */
class Regex {
    class Impl;
    std::unique_ptr<const Impl> _impl;

    explicit Regex(std::unique_ptr<const Impl> impl);
public:
    // TODO consider using type-safe parameter instead.
    enum Options {
        None              = 0,
        IgnoreCase        = 1,
        DotMatchesNewline = 2
    };

    //Default constructed object is invalid
    Regex();

    ~Regex();
    Regex(Regex&&) noexcept;
    Regex& operator=(Regex&&) noexcept;

    [[nodiscard]] bool valid() const noexcept { return bool(_impl); }

    [[nodiscard]] bool parsed_ok() const noexcept;

    [[nodiscard]] bool partial_match(std::string_view input) const noexcept;
    [[nodiscard]] bool full_match(std::string_view input) const noexcept;

    // Returns a pair of <lower bound, upper bound> prefix strings that constrain the possible
    // match-able range of inputs for this regex. If there is no shared prefix, or if extracting
    // the range fails, the strings will be empty.
    // Important: this is _only_ semantically valid if the regex is strictly start-anchored, i.e.
    // all possible matching paths start with '^'.
    // This method does _not_ validate that the regex is strictly start-anchored.
    [[nodiscard]] std::pair<std::string, std::string> possible_anchored_match_prefix_range() const;

    [[nodiscard]] static Regex from_pattern(std::string_view pattern, uint32_t opt_flags = Options::None);

    // Utility matchers for non-precompiled expressions.
    [[nodiscard]] static bool partial_match(std::string_view input, std::string_view pattern) noexcept;
    [[nodiscard]] static bool full_match(std::string_view input, std::string_view pattern) noexcept;
};

}

