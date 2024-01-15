// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "tokenizereader.h"

namespace vsm {

void
TokenizeReader::fold(ucs4_t c) {
    const char *repl = Fast_NormalizeWordFolder::ReplacementString(c);
    if (repl != nullptr) {
        size_t repllen = strlen(repl);
        if (repllen > 0) {
            _q = Fast_UnicodeUtil::ucs4copy(_q,repl);
        }
    } else {
        c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
        *_q++ = c;
    }
}

}
