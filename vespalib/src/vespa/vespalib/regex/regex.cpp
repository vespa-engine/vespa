// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "regex.h"
#include <re2/re2.h>
#include <cassert>
#include <cstdint>

namespace vespalib {

using re2::StringPiece;

// All RE2 instances use a Quiet option to prevent the library from
// complaining to stderr if pattern compilation fails.

Regex::Regex(std::unique_ptr<const Impl> impl)
    : _impl(std::move(impl))
{}

Regex::Regex() : _impl() {}

Regex::Regex(Regex&&) noexcept = default;
Regex& Regex::operator=(Regex&&) noexcept = default;

Regex::~Regex() = default;

class Regex::Impl {
    RE2 _regex;
public:
    Impl(std::string_view pattern, const re2::RE2::Options& opts)
        : _regex(StringPiece(pattern.data(), pattern.size()), opts)
    {}

    bool parsed_ok() const noexcept {
        return _regex.ok();
    }

    bool partial_match(std::string_view input) const noexcept {
        assert(input.size() <= INT32_MAX);
        if (!_regex.ok()) {
            return false;
        }
        return RE2::PartialMatch(StringPiece(input.data(), input.size()), _regex);
    }

    bool full_match(std::string_view input) const noexcept {
        assert(input.size() <= INT32_MAX);
        if (!_regex.ok()) {
            return false;
        }
        return RE2::FullMatch(StringPiece(input.data(), input.size()), _regex);
    }
};

Regex Regex::from_pattern(std::string_view pattern, uint32_t opt_mask) {
    assert(pattern.size() <= INT32_MAX); // StringPiece limitation
    RE2::Options opts;
    opts.set_log_errors(false);
    if ((opt_mask & Options::IgnoreCase) != 0) {
        opts.set_case_sensitive(false);
    }
    if ((opt_mask & Options::DotMatchesNewline) != 0) {
        opts.set_dot_nl(true);
    }
    return Regex(std::make_unique<const Impl>(pattern, opts));
}

bool Regex::parsed_ok() const noexcept {
    return _impl->parsed_ok();
}

bool Regex::partial_match(std::string_view input) const noexcept {
    return _impl->partial_match(input);
}

bool Regex::full_match(std::string_view input) const noexcept {
    return _impl->full_match(input);
}

bool Regex::partial_match(std::string_view input, std::string_view pattern) noexcept {
    assert(pattern.size() <= INT32_MAX);
    Impl impl(pattern, RE2::Quiet);
    return impl.partial_match(input);
}

bool Regex::full_match(std::string_view input, std::string_view pattern) noexcept {
    assert(pattern.size() <= INT32_MAX);
    Impl impl(pattern, RE2::Quiet);
    return impl.full_match(input);
}

}
