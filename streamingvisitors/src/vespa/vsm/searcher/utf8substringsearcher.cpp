// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vsm/searcher/utf8substringsearcher.h>

using search::byte;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

std::unique_ptr<FieldSearcher>
UTF8SubStringFieldSearcher::duplicate() const
{
    return std::make_unique<UTF8SubStringFieldSearcher>(*this);
}

size_t
UTF8SubStringFieldSearcher::matchTerms(const FieldRef & f, const size_t mintsz)
{
    const byte * n = reinterpret_cast<const byte *> (f.data());
    if ( f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * fntemp = &(*_buf.get())[0];
    BufferWrapper wrapper(fntemp);
    size_t fl = skipSeparators(n, f.size(), wrapper);
    const cmptype_t * fn(fntemp);
    const cmptype_t * fe = fn + fl;
    const cmptype_t * fre = fe - mintsz;
    termcount_t words(0);
    for(words = 0; fn <= fre; ) {
        for (auto qt : _qtl) {
            const cmptype_t * term;
            termsize_t tsz = qt->term(term);

            const cmptype_t *tt=term, *et=term+tsz, *fnt=fn;
            for (; (tt < et) && (*tt == *fnt); tt++, fnt++);
            if (tt == et) {
                addHit(*qt, words);
            }
        }
        if ( ! Fast_UnicodeUtil::IsWordChar(*fn++) ) {
            words++;
            for(; (fn < fre) && ! Fast_UnicodeUtil::IsWordChar(*fn); fn++ );
        }
    }

    NEED_CHAR_STAT(addAnyUtf8Field(f.size()));
    return words + 1; // we must also count the last word
}

size_t
UTF8SubStringFieldSearcher::matchTerm(const FieldRef & f, QueryTerm & qt)
{
    return matchTermSubstring(f, qt);
}

}
