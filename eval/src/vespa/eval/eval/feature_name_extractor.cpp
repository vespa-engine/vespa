// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feature_name_extractor.h"

namespace vespalib::eval {

namespace {

struct LegalChar {
    bool legal[256];
    LegalChar(std::initializer_list<uint8_t> extra_chars) {
        for (int c = 0; c < 256; ++c) {
            legal[c] = isalnum(c);
        }
        for (uint8_t c: extra_chars) {
            legal[c] = true;
        }
    }
    bool is_legal(uint8_t c) { return legal[c]; }
};

static LegalChar prefix({'_', '$', '@'});
static LegalChar suffix({'_', '.', '$', '@'});

struct CountParen {
    size_t depth = 0;
    bool quoted = false;
    bool escaped = false;
    bool done(char c) {
        if (quoted) {
            if (escaped) {
                escaped = false;
            } else {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    quoted = false;
                }
            }
        } else {
            if (c == '"') {
                quoted = true;
            } else if (c == '(') {
                ++depth;
            } else if (c == ')') {
                if (--depth == 0) {
                    return true;
                }
            }
        }
        return false;
    }
};

} // namespace <unnamed>

void
FeatureNameExtractor::extract_symbol(const char *pos_in, const char *end_in,
                                     const char *&pos_out, vespalib::string &symbol_out) const
{
    while ((pos_in < end_in) && prefix.is_legal(*pos_in)) {
        symbol_out.push_back(*pos_in++);
    }
    if ((pos_in < end_in) && (*pos_in == '(')) {
        CountParen paren;
        while (pos_in < end_in) {
            symbol_out.push_back(*pos_in);
            if (paren.done(*pos_in++)) {
                break;
            }
        }
    }
    if ((pos_in < end_in) && (*pos_in == '.')) {
        symbol_out.push_back(*pos_in++);
        while ((pos_in < end_in) && suffix.is_legal(*pos_in)) {
            symbol_out.push_back(*pos_in++);
        }
    }
    pos_out = pos_in;
}

}
