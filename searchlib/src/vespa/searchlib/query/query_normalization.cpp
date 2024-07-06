// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_normalization.h"
#include <vespa/fastlib/text/normwordfolder.h>
#include <ostream>

namespace search {

namespace {

const char *
to_str(search::Normalizing norm) noexcept {
    switch (norm) {
        case search::Normalizing::NONE:
            return "NONE";
        case search::Normalizing::LOWERCASE:
            return "LOWERCASE";
        case search::Normalizing::LOWERCASE_AND_FOLD:
            return "LOWERCASE_AND_FOLD";
    }
    abort();
}

Normalizing
requireFold(TermType type, Normalizing normalizing) {
    if (normalizing == Normalizing::NONE) return Normalizing::NONE;
    if (normalizing == Normalizing::LOWERCASE) return Normalizing::LOWERCASE;
    if (type == TermType::EXACTSTRINGTERM) return Normalizing::LOWERCASE;
    return ((type == TermType::WORD) || (type == TermType::SUBSTRINGTERM) ||
            (type == TermType::PREFIXTERM) || (type == TermType::SUFFIXTERM))
           ? Normalizing::LOWERCASE_AND_FOLD
           : Normalizing::NONE;
}

vespalib::string
fold(std::string_view s) {
    const auto * curr = reinterpret_cast<const unsigned char *>(s.data());
    const unsigned char * end = curr + s.size();
    vespalib::string folded;
    for (; curr < end;) {
        uint32_t c_ucs4 = *curr;
        if (c_ucs4 < 0x80) {
            folded += Fast_NormalizeWordFolder::lowercase_and_fold_ascii(*curr++);
        } else {
            c_ucs4 = Fast_UnicodeUtil::GetUTF8CharNonAscii(curr);
            const char *repl = Fast_NormalizeWordFolder::ReplacementString(c_ucs4);
            if (repl != nullptr) {
                size_t repllen = strlen(repl);
                folded.append(repl, repllen);
            } else {
                c_ucs4 = Fast_NormalizeWordFolder::lowercase_and_fold(c_ucs4);
                char tmp[6];
                const char * tmp_end = Fast_UnicodeUtil::utf8cput(tmp, c_ucs4);
                folded.append(tmp, tmp_end - tmp);
            }
        }
    }
    return folded;
}

vespalib::string
lowercase(std::string_view s) {
    const auto * curr = reinterpret_cast<const unsigned char *>(s.data());
    const unsigned char * end = curr + s.size();
    vespalib::string folded;
    for (; curr < end;) {
        uint32_t c_ucs4 = *curr;
        if (c_ucs4 < 0x80) {
            folded += static_cast<char>(Fast_NormalizeWordFolder::lowercase_ascii(*curr++));
        } else {
            c_ucs4 = Fast_NormalizeWordFolder::lowercase(Fast_UnicodeUtil::GetUTF8CharNonAscii(curr));
            char tmp[6];
            const char * tmp_end = Fast_UnicodeUtil::utf8cput(tmp, c_ucs4);
            folded.append(tmp, tmp_end - tmp);
        }
    }
    return folded;
}

}

std::ostream &
operator<<(std::ostream &os, Normalizing n) {
    os << to_str(n);
    return os;
}

vespalib::string
QueryNormalization::optional_fold(std::string_view s, TermType type, Normalizing normalizing) {
    switch ( requireFold(type, normalizing)) {
        case Normalizing::NONE: return vespalib::string(s);
        case Normalizing::LOWERCASE: return lowercase(s);
        case Normalizing::LOWERCASE_AND_FOLD: return fold(s);
    }
    return vespalib::string(s);
}

}
