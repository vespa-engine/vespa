// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "utf8suffixstringfieldsearcher.h"
#include "tokenizereader.h"

using search::byte;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

std::unique_ptr<FieldSearcher>
UTF8SuffixStringFieldSearcher::duplicate() const
{
    return std::make_unique<UTF8SuffixStringFieldSearcher>(*this);
}

size_t
UTF8SuffixStringFieldSearcher::matchTerms(const FieldRef & f, size_t mintsz)
{
    (void) mintsz;
    termcount_t words = 0;
    if (f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * dstbuf = &(*_buf.get())[0];

    TokenizeReader reader(reinterpret_cast<const byte *> (f.data()), f.size(), dstbuf);
    while ( reader.hasNext() ) {
        size_t tokenlen = reader.tokenize(normalize_mode());
        for (auto qt : _qtl) {
            const cmptype_t * term;
            termsize_t tsz = qt->term(term);
            if (matchTermSuffix(term, tsz, dstbuf, tokenlen)) {
                addHit(*qt, words);
            }
        }
        words++;
    }
    return words;
}

size_t
UTF8SuffixStringFieldSearcher::matchTerm(const FieldRef & f, QueryTerm & qt)
{
    return matchTermSuffix(f, qt);
}

}
