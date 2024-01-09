// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "strchrfieldsearcher.h"
#include <vespa/fastlib/text/normwordfolder.h>

namespace vsm {

/**
 * This class is the base class for all utf8 string searchers.
 * It contains utility functions used by the other searchers.
 * As normal the prepare method is called
 * after the query is built. A SharedSearcherBuf is used given to it. This is a
 * buffer that is shared among all searchers that are run in the same context.
 * Reuse of this buffer ensures better cache hit ratio because this is just a
 * scratchpad for tokenizing. It will grow till the max size and stay there.
 **/
class UTF8StringFieldSearcherBase : public StrChrFieldSearcher
{
public:
    /**
     * Template class that wraps an ucs4 buffer.
     * Used when invoking skipSeparators() during substring matching.
     **/
    class BufferWrapper
    {
    protected:
        ucs4_t * _bbuf;
        ucs4_t * _cbuf;

    public:
        explicit BufferWrapper(ucs4_t * buf) noexcept : _bbuf(buf), _cbuf(buf) { }
        BufferWrapper(ucs4_t * buf, size_t *) noexcept : _bbuf(buf), _cbuf(buf) { }
        void onCharacter(ucs4_t ch, size_t) { *_cbuf++ = ch; }
        void onOffset(size_t) { }
        void incBuf(size_t inc) { _cbuf += inc; }
        ucs4_t * getBuf() { return _cbuf; }
        bool valid() const noexcept { return true; }
        size_t size() const noexcept { return (_cbuf - _bbuf); }
        bool hasOffsets() const noexcept { return false; }
    };

    /**
     * Template class that wraps an offset buffer in addition to an ucs4 buffer.
     * The offset buffer contains offsets into the original utf8 buffer.
     **/
    class OffsetWrapper : public BufferWrapper
    {
    private:
        size_t * _boff;
        size_t * _coff;

    public:
        explicit OffsetWrapper(ucs4_t * buf, size_t * offsets) noexcept : BufferWrapper(buf), _boff(offsets), _coff(offsets) {}
        void onCharacter(ucs4_t ch, size_t of) { *_cbuf++ = ch; *_coff++ = of; }
        void onOffset(size_t of) { *_coff++ = of; }
        bool valid() const noexcept { return (size() == (size_t)(_coff - _boff)); }
        bool hasOffsets() const noexcept { return true; }
    };

protected:
    SharedSearcherBuf _buf;

    using byte = search::byte;
    using Normalizing = search::streaming::Normalizing;

    class TokenizeReader {
    public:
        TokenizeReader(const byte *p, uint32_t len, ucs4_t *q) noexcept
            : _p(p),
              _p_end(p + len),
              _q(q),
              _q_start(q)
        {}
        ucs4_t next() noexcept { return Fast_UnicodeUtil::GetUTF8Char(_p); }
        void normalize(ucs4_t c, Normalizing normalize_mode) {
            switch (normalize_mode) {
                case Normalizing::LOWERCASE:
                    c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
                    [[fallthrough]];
                case Normalizing::NONE:
                    *_q++ = c;
                    break;
                case Normalizing::LOWERCASE_AND_FOLD:
                    fold(c);
                    break;
            }
        }
        bool hasNext() const noexcept { return _p < _p_end; }
        const byte * p() const noexcept { return _p; }
        size_t complete() noexcept {
            *_q = 0;
            size_t token_len = _q - _q_start;
            _q = _q_start;
            return token_len;
        }
    private:
        void fold(ucs4_t c) {
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
        void lowercase(ucs4_t c) {
            c = Fast_NormalizeWordFolder::lowercase_and_fold(c);
            *_q++ = c;
        }
        const byte *_p;
        const byte *_p_end;
        ucs4_t     *_q;
        ucs4_t     *_q_start;
    };


    template<typename Reader>
    void tokenize(Reader & reader);

    Normalizing normalize_mode() const noexcept {
        switch (match_type()) {
            case EXACT: return Normalizing::LOWERCASE;
            case CASED: return Normalizing::NONE;
            default: return Normalizing::LOWERCASE_AND_FOLD;
        }
        return Normalizing::LOWERCASE_AND_FOLD;
    }

    /**
     * Matches the given query term against the words in the given field reference
     * using exact or prefix match strategy.
     *
     * @param f  the field reference to match against.
     * @param qt the query term trying to match.
     * @return   the number of words in the field ref.
     **/
    size_t matchTermRegular(const FieldRef & f, search::streaming::QueryTerm & qt);

    /**
     * Matches the given query term against the characters in the given field reference
     * using substring match strategy.
     *
     * @param f  the field reference to match against.
     * @param qt the query term trying to match.
     * @return   the number of words in the field ref.
     **/
    size_t matchTermSubstring(const FieldRef & f, search::streaming::QueryTerm & qt);

    /**
     * Matches the given query term against the words in the given field reference
     * using suffix match strategy.
     *
     * @param f  the field reference to match against.
     * @param qt the query term trying to match.
     * @return   the number of words in the field ref.
     **/
    size_t matchTermSuffix(const FieldRef & f, search::streaming::QueryTerm & qt);

    /**
     * Matches the given query term against the words in the given field reference
     * using exact match strategy.
     *
     * @param f  the field reference to match against.
     * @param qt the query term trying to match.
     * @return   the number of words in the field ref.
     **/
    size_t matchTermExact(const FieldRef & f, search::streaming::QueryTerm & qt);

public:
    explicit UTF8StringFieldSearcherBase(FieldIdT fId);
    ~UTF8StringFieldSearcherBase() override;
    void prepare(search::streaming::QueryTermList& qtl,
                 const SharedSearcherBuf& buf,
                 const vsm::FieldPathMapT& field_paths,
                 search::fef::IQueryEnvironment& query_env) override;
    /**
     * Matches the given query term against the given word using suffix match strategy.
     *
     * @param term the buffer with the term.
     * @param termLen the length of the term.
     * @param word the buffer with the word.
     * @param wordlen the length of the word.
     * @return true if the term matches the word.
     **/
    static bool matchTermSuffix(const cmptype_t * term, size_t termlen,
                                const cmptype_t * word, size_t wordlen);

    /**
     * Checks whether the given character is a separator character.
     **/
    static bool isSeparatorCharacter(ucs4_t);

    /**
     * Transforms the given utf8 array into an array of ucs4 characters.
     * Folding is performed. Separator characters are skipped.
     **/
    template <typename T>
    size_t skipSeparators(const search::byte * p, size_t sz, T & dstbuf);

};

}

