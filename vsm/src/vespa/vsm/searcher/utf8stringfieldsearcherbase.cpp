// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "utf8stringfieldsearcherbase.h"
#include <cassert>

using search::streaming::QueryTerm;
using search::streaming::QueryTermList;
using search::byte;

namespace vsm {

const byte *
UTF8StringFieldSearcherBase::tokenize(const byte * p, size_t maxSz, cmptype_t * dstbuf, size_t & tokenlen)
{
    if (maxSz > 0) {
        maxSz--;
    }
    ucs4_t c(*p);
    ucs4_t *q(dstbuf);
    const byte * end(p+maxSz);

    // Skip non-word characters between words
    for (; p < end; ) {
        if (c < 128) {
            if (!c) { break; }
            p++;
            if (__builtin_expect(_isWord[c], false)) {
                *q++ = _foldCase[c];
                c = 0;
            } else {
                c = *p;
            }
        } else {
            const byte * oldP(p);
            c = Fast_UnicodeUtil::GetUTF8CharNonAscii(p);
            if (Fast_UnicodeUtil::IsWordChar(c)) {
                _utf8Count[p-oldP-1]++;
                const char *repl = ReplacementString(c);
                if (repl != NULL) {
                    size_t repllen = strlen(repl);
                    if (repllen > 0) {
                        q = Fast_UnicodeUtil::ucs4copy(q,repl);
                    }
                } else {
                    c = ToFold(c);
                    *q++ = c;
                }
                break;
            } else {
                if (c == _BadUTF8Char) {
                    _badUtf8Count++;
                } else {
                    _utf8Count[p-oldP-1]++;
                }
                c = *p;
            }
        }
    }

    c = *p;  // Next char
    for (; p < end;) {
        if (c < 128) {             // Common case, ASCII
            if (!c) { break; }
            p++;
            if (__builtin_expect(!_isWord[c], false)) {
                c = 0;
            } else {
                *q++ = _foldCase[c];
                c = *p;
            }
        } else {
            const byte * oldP(p);
            c = Fast_UnicodeUtil::GetUTF8CharNonAscii(p);
            if (__builtin_expect(Fast_UnicodeUtil::IsWordChar(c), false)) {
                _utf8Count[p-oldP-1]++;
                const char *repl = ReplacementString(c);
                if (repl != NULL) {
                    size_t repllen = strlen(repl);
                    if (repllen > 0) {
                        q = Fast_UnicodeUtil::ucs4copy(q,repl);
                    }
                } else {
                    c = ToFold(c);
                    *q++ = c;
                }

                c = *p;
            } else {
                if (c == _BadUTF8Char) {
                    _badUtf8Count++;
                } else {
                    _utf8Count[p-oldP-1]++;
                }
                break;
            }
        }
    }
    *q = 0;
    tokenlen = q - dstbuf;
    return p;
}

size_t
UTF8StringFieldSearcherBase::matchTermRegular(const FieldRef & f, QueryTerm & qt)
{
    termcount_t words(0);
    const byte * n = reinterpret_cast<const byte *> (f.data());
    // __builtin_prefetch(n, 0, 0);
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    const byte * e = n + f.size();
    if ( f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * fn = &(*_buf.get())[0];
    size_t fl(0);

    for( ; n < e; ) {
        if (!*n) { _zeroCount++; n++; }
        n = tokenize(n, _buf->capacity(), fn, fl);
        if ((tsz <= fl) && (prefix() || qt.isPrefix() || (tsz == fl))) {
            const cmptype_t *tt=term, *et=term+tsz;
            for (const cmptype_t *fnt=fn; (tt < et) && (*tt == *fnt); tt++, fnt++);
            if (tt == et) {
                addHit(qt, words);
            }
        }
        words++;
    }
    NEED_CHAR_STAT(addAnyUtf8Field(f.size()));
    return words;
}

size_t
UTF8StringFieldSearcherBase::matchTermExact(const FieldRef & f, QueryTerm & qt)
{
    const byte * n = reinterpret_cast<const byte *> (f.data());
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    const cmptype_t * eterm = term+tsz;
    const byte * e = n + f.size();
    if (tsz <= f.size()) {
        bool equal(true);
        for (; equal && (n < e) && (term < eterm); term++) {
            if (*term < 0x80) {
                equal = (*term == _foldCase[*n++]);
            } else {
                cmptype_t c = ToFold(Fast_UnicodeUtil::GetUTF8CharNonAscii(n));
                equal = (*term == c);
            }
        }
        if (equal && (term == eterm) && (qt.isPrefix() || (n == e))) {
            addHit(qt,0);
        }
    }
    NEED_CHAR_STAT(addAnyUtf8Field(f.size()));
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
    NEED_CHAR_STAT(addAnyUtf8Field(f.size()));
    return words + 1; // we must also count the last word
}

size_t
UTF8StringFieldSearcherBase::matchTermSuffix(const FieldRef & f, QueryTerm & qt)
{
    termcount_t words = 0;
    const byte * srcbuf = reinterpret_cast<const byte *> (f.data());
    const byte * srcend = srcbuf + f.size();
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    if (f.size() >= _buf->size()) {
        _buf->reserve(f.size() + 1);
    }
    cmptype_t * dstbuf = &(*_buf.get())[0];
    size_t tokenlen = 0;

    for( ; srcbuf < srcend; ) {
        if (*srcbuf == 0) {
            ++_zeroCount;
            ++srcbuf;
        }
        srcbuf = tokenize(srcbuf, _buf->capacity(), dstbuf, tokenlen);
        if (matchTermSuffix(term, tsz, dstbuf, tokenlen)) {
            addHit(qt, words);
        }
        words++;
    }
    return words;
}

UTF8StringFieldSearcherBase::UTF8StringFieldSearcherBase() :
    StrChrFieldSearcher(),
    Fast_NormalizeWordFolder(),
    Fast_UnicodeUtil()
{
}

UTF8StringFieldSearcherBase::UTF8StringFieldSearcherBase(FieldIdT fId) :
    StrChrFieldSearcher(fId),
    Fast_NormalizeWordFolder(),
    Fast_UnicodeUtil()
{
}

UTF8StringFieldSearcherBase::~UTF8StringFieldSearcherBase() {}

void
UTF8StringFieldSearcherBase::prepare(QueryTermList & qtl, const SharedSearcherBuf & buf)
{
    StrChrFieldSearcher::prepare(qtl, buf);
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
                dstbuf.onCharacter(_foldCase[c], (oldP - b));
            }
        } else {
            c = Fast_UnicodeUtil::GetUTF8CharNonAscii(p);
            const char *repl = ReplacementString(c);
            if (repl != NULL) {
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
                c = ToFold(c);
                dstbuf.onCharacter(c, (oldP - b));
            }
            if (c == _BadUTF8Char) {
                _badUtf8Count++;
            } else {
                _utf8Count[p-oldP-1]++;
            }
        }
    }
    assert(dstbuf.valid());
    return dstbuf.size();
}

template unsigned long UTF8StringFieldSearcherBase::skipSeparators<UTF8StringFieldSearcherBase::BufferWrapper>(unsigned char const*, unsigned long, UTF8StringFieldSearcherBase::BufferWrapper&);
template unsigned long UTF8StringFieldSearcherBase::skipSeparators<UTF8StringFieldSearcherBase::OffsetWrapper>(unsigned char const*, unsigned long, UTF8StringFieldSearcherBase::OffsetWrapper&);

}
