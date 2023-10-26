// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "explicit_levenshtein_dfa.h"
#include "implicit_levenshtein_dfa.h"
#include "table_dfa.h"
#include "levenshtein_dfa.h"
#include "unicode_utils.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <memory>

namespace vespalib::fuzzy {

LevenshteinDfa::LevenshteinDfa(std::unique_ptr<Impl> impl) noexcept
    : _impl(std::move(impl))
{}

LevenshteinDfa::LevenshteinDfa(LevenshteinDfa&&) noexcept = default;
LevenshteinDfa& LevenshteinDfa::operator=(LevenshteinDfa&&) noexcept = default;

LevenshteinDfa::~LevenshteinDfa() = default;

LevenshteinDfa::MatchResult
LevenshteinDfa::match(std::string_view u8str) const {
    return _impl->match(u8str);
}

LevenshteinDfa::MatchResult
LevenshteinDfa::match(std::string_view u8str, std::string& successor_out) const {
    return _impl->match(u8str, successor_out);
}

LevenshteinDfa::MatchResult
LevenshteinDfa::match(std::string_view u8str, std::vector<uint32_t>& successor_out) const {
    return _impl->match(u8str, successor_out);
}

size_t LevenshteinDfa::memory_usage() const noexcept {
    return _impl->memory_usage();
}

void LevenshteinDfa::dump_as_graphviz(std::ostream& out) const {
    _impl->dump_as_graphviz(out);
}

LevenshteinDfa LevenshteinDfa::build(std::string_view target_string, uint8_t max_edits, Casing casing, DfaType dfa_type) {
    if (max_edits != 1 && max_edits != 2) {
        throw std::invalid_argument(make_string("Levenshtein DFA max_edits must be in {1, 2}, was %u", max_edits));
    }
    const bool is_cased = (casing == Casing::Cased);
    auto target_string_u32 = is_cased ? utf8_string_to_utf32(target_string)
                                      : utf8_string_to_utf32_lowercased(target_string);
    if (dfa_type == DfaType::Implicit) {
        if (max_edits == 1) {
            return LevenshteinDfa(std::make_unique<ImplicitLevenshteinDfa<FixedMaxEditDistanceTraits<1>>>(std::move(target_string_u32), is_cased));
        } else { // max_edits == 2
            return LevenshteinDfa(std::make_unique<ImplicitLevenshteinDfa<FixedMaxEditDistanceTraits<2>>>(std::move(target_string_u32), is_cased));
        }
    } else if(dfa_type == DfaType::Explicit) {
        if (max_edits == 1) {
            return ExplicitLevenshteinDfaBuilder<FixedMaxEditDistanceTraits<1>>(std::move(target_string_u32), is_cased).build_dfa();
        } else { // max_edits == 2
            return ExplicitLevenshteinDfaBuilder<FixedMaxEditDistanceTraits<2>>(std::move(target_string_u32), is_cased).build_dfa();
        }
    } else { // DfaType::Table
        if (max_edits == 1) {
            return LevenshteinDfa(std::make_unique<TableDfa<1>>(std::move(target_string_u32), is_cased));
        } else { // max_edits == 2
            return LevenshteinDfa(std::make_unique<TableDfa<2>>(std::move(target_string_u32), is_cased));
        }
    }
}

LevenshteinDfa LevenshteinDfa::build(std::string_view target_string, uint8_t max_edits, Casing casing) {
    // TODO automatically select implementation based on target length/max edits?
    //  Suggestion:
    //   - Explicit DFA iff (k == 1 && |target| <= 256) || (k == 2 && |target| <= 64).
    //   - Implicit DFA otherwise.
    //  This keeps memory overhead < 64k and DFA construction time < 300 usec (measured on
    //  an M1 Pro; your mileage may vary etc).
    //  Ideally the implicit DFA would always be the fastest (or at least approximately as
    //  fast as the explicit DFA), but this is not yet the case.
    return build(target_string, max_edits, casing, DfaType::Implicit);
}

std::ostream& operator<<(std::ostream& os, const LevenshteinDfa::MatchResult& mos) {
    if (mos.matches()) {
        os << "match(" << static_cast<int>(mos.edits()) << " edits)";
    } else {
        os << "mismatch";
    }
    return os;
}

std::ostream& operator<<(std::ostream& os, LevenshteinDfa::DfaType dt) {
    if (dt == LevenshteinDfa::DfaType::Implicit) {
        os << "Implicit";
    } else if (dt == LevenshteinDfa::DfaType::Explicit) {
        os << "Explicit";
    } else {
        assert(dt == LevenshteinDfa::DfaType::Table);
        os << "Table";
    }
    return os;
}

std::ostream& operator<<(std::ostream& os, LevenshteinDfa::Casing c) {
    if (c == LevenshteinDfa::Casing::Uncased) {
        os << "Uncased";
    } else {
        assert(c == LevenshteinDfa::Casing::Cased);
        os << "Cased";
    }
    return os;
}

}
