// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utf8stringfieldsearcherbase.h"
#include "tokenizereader.h"
#include <cassert>

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;
using search::byte;

namespace vsm {

size_t
UTF8StringFieldSearcherBase::matchTermRegular(const FieldRef & f, QueryTerm & qt)
{
    termcount_t words(0);
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    if ( f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * fn = _buf->data();

    TokenizeReader reader(reinterpret_cast<const byte *> (f.data()), f.size(), fn);
    while ( reader.hasNext() ) {
        size_t fl = reader.tokenize(normalize_mode());
        if ((tsz <= fl) && (prefix() || qt.isPrefix() || (tsz == fl))) {
            const cmptype_t *tt=term, *et=term+tsz;
            for (const cmptype_t *fnt=fn; (tt < et) && (*tt == *fnt); tt++, fnt++);
            if (tt == et) {
                addHit(qt, words);
            }
        }
        words++;
    }
    return words;
}

size_t
UTF8StringFieldSearcherBase::matchTermExact(const FieldRef & f, QueryTerm & qt)
{
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    const cmptype_t * eterm = term+tsz;
    if ( f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * fn = _buf->data();
    if (tsz <= f.size()) {
        bool equal(true);
        Normalizing norm_mode = normalize_mode();
        TokenizeReader reader(reinterpret_cast<const byte *> (f.data()), f.size(), fn);
        while (equal && reader.hasNext() && (term < eterm)) {
            reader.normalize(reader.next(), norm_mode);
            size_t len = reader.complete();
            for (size_t i(0); i < len; i++) {
                equal = (term[i] == fn[i]);
            }
            term += len;
        }
        if (equal && (term == eterm) && (qt.isPrefix() || ! reader.hasNext())) {
            addHit(qt,0);
        }
    }
    return 1;
}

size_t
UTF8StringFieldSearcherBase::matchTermSubstring(const FieldRef & f, QueryTerm & qt)
{
    if (qt.termLen() == 0) { return 0; }
    const byte * n = reinterpret_cast<const byte *> (f.data());
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    if ( f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * fntemp = &(*_buf.get())[0];
    BufferWrapper wrapper(fntemp);
    size_t fl = skipSeparators(n, f.size(), wrapper);
    const cmptype_t * fn(fntemp);
    const cmptype_t * fe = fn + fl;
    const cmptype_t * fre = fe - tsz;
    termcount_t words(0);
    for(words = 0; fn <= fre; ) {
        const cmptype_t *tt=term, *et=term+tsz, *fnt=fn;
        for (; (tt < et) && (*tt == *fnt); tt++, fnt++);
        if (tt == et) {
            fn = fnt;
            addHit(qt, words);
        } else {
            if ( ! Fast_UnicodeUtil::IsWordChar(*fn++) ) {
                words++;
                for(; (fn < fre) && ! Fast_UnicodeUtil::IsWordChar(*fn) ; fn++ );
            }
        }
    }
    return words + 1; // we must also count the last word
}

size_t
UTF8StringFieldSearcherBase::matchTermSuffix(const FieldRef & f, QueryTerm & qt)
{
    termcount_t words = 0;
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    if (f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * dstbuf = _buf->data();

    TokenizeReader reader(reinterpret_cast<const byte *> (f.data()), f.size(), dstbuf);
    while ( reader.hasNext() ) {
        size_t tokenlen = reader.tokenize(normalize_mode());
        if (matchTermSuffix(term, tsz, dstbuf, tokenlen)) {
            addHit(qt, words);
        }
        words++;
    }
    return words;
}

UTF8StringFieldSearcherBase::UTF8StringFieldSearcherBase(FieldIdT fId) :
    StrChrFieldSearcher(fId)
{
}

UTF8StringFieldSearcherBase::~UTF8StringFieldSearcherBase() = default;

void
UTF8StringFieldSearcherBase::prepare(search::streaming::QueryTermList& qtl,
                                     const SharedSearcherBuf& buf,
                                     const vsm::FieldPathMapT& field_paths,
                                     search::fef::IQueryEnvironment& query_env)
{
    StrChrFieldSearcher::prepare(qtl, buf, field_paths, query_env);
    _buf = buf;
}

bool
UTF8StringFieldSearcherBase::matchTermSuffix(const cmptype_t * term, size_t termlen,
                                             const cmptype_t * word, size_t wordlen)
{
    if ((termlen <= wordlen)) {
        const cmptype_t * titr = term + termlen - 1;
        const cmptype_t * witr = word + wordlen - 1;
        bool hit = true;
        // traverse the term and the word back to front
        for (; titr >= term; --titr, --witr) {
            if (*titr != *witr) {
                hit = false;
                break;
            }
        }
        return hit;
    }
    return false;
}

bool
UTF8StringFieldSearcherBase::isSeparatorCharacter(ucs4_t c)
{
    return ((c < 0x20) && (c != '\n') && (c != '\t'));
}

template <typename T>
size_t
UTF8StringFieldSearcherBase::skipSeparators(const search::byte * p, size_t sz, T & dstbuf) {
    const search::byte * e(p+sz);
    const search::byte * b(p);

    for(; p < e; ) {
        ucs4_t c(*p);
        const search::byte * oldP(p);
        if (c < 128) {
            p++;
            if (!isSeparatorCharacter(c)) {
                dstbuf.onCharacter(Fast_NormalizeWordFolder::lowercase_and_fold_ascii(c), (oldP - b));
            }
        } else {
            c = Fast_UnicodeUtil::GetUTF8CharNonAscii(p);
            const char *repl = Fast_NormalizeWordFolder::ReplacementString(c);
            if (repl != nullptr) {
                size_t repllen = strlen(repl);
                if (repllen > 0) {
                    ucs4_t * buf = dstbuf.getBuf();
                    ucs4_t * newBuf = Fast_UnicodeUtil::ucs4copy(buf, repl);
                    if (dstbuf.hasOffsets()) {
                        for (; buf < newBuf; ++buf) {
                            dstbuf.incBuf(1);
                            dstbuf.onOffset(oldP - b);
                        }
                    } else {
                        dstbuf.incBuf(newBuf - buf);
                    }
                }
            } else {
                c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
                dstbuf.onCharacter(c, (oldP - b));
            }
            if (c == Fast_UnicodeUtil::_BadUTF8Char) {
                _badUtf8Count++;
            }
        }
    }
    assert(dstbuf.valid());
    return dstbuf.size();
}

template unsigned long UTF8StringFieldSearcherBase::skipSeparators<UTF8StringFieldSearcherBase::BufferWrapper>(unsigned char const*, unsigned long, UTF8StringFieldSearcherBase::BufferWrapper&);
template unsigned long UTF8StringFieldSearcherBase::skipSeparators<UTF8StringFieldSearcherBase::OffsetWrapper>(unsigned char const*, unsigned long, UTF8StringFieldSearcherBase::OffsetWrapper&);

}
