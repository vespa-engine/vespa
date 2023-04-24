// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "utf8substringsnippetmodifier.h"
#include <vespa/juniper/juniper_separators.h>
#include <cassert>

using search::byte;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

namespace vsm {

std::unique_ptr<FieldSearcher>
UTF8SubstringSnippetModifier::duplicate() const
{
    return std::make_unique<UTF8SubstringSnippetModifier>(*this);
}

size_t
UTF8SubstringSnippetModifier::matchTerms(const FieldRef & f, const size_t mintsz)
{
    _modified->reset();
    _readPtr = f.data();
    const byte * src = reinterpret_cast<const byte *> (f.data());
    // resize ucs4 buffer
    if (f.size() >= _buf->size()) {
        _buf->resize(f.size() + 1);
    }
    // resize offset buffers
    if (f.size() >= _offsets->size()) {
        _offsets->resize(f.size() + 1);
    }
    // resize modified buffer
    if (f.size() + 16 > _modified->getLength()) {
        _modified->resize(f.size() + 16); // make room for some unit separators
    }
    cmptype_t * dbegin = &(*_buf.get())[0];
    OffsetWrapper wrapper(dbegin, &(*_offsets)[0]);
    size_t numchars = skipSeparators(src, f.size(), wrapper);
    const cmptype_t * ditr = dbegin;
    const cmptype_t * dend = ditr + numchars;
    const cmptype_t * drend = dend - mintsz;
    termcount_t words = 0;
    for(; ditr <= drend; ) {
        for (auto qt : _qtl) {
            const cmptype_t * term;
            termsize_t tsz = qt->term(term);

            const cmptype_t * titr = term;
            const cmptype_t * tend = term + tsz;
            const cmptype_t * dtmp = ditr;
            for (; (titr < tend) && (*titr == *dtmp); ++titr, ++dtmp);
            if (titr == tend) {
                const char * mbegin = f.data() + (*_offsets)[ditr - dbegin];
                const char * mend = f.data() + ((dtmp < dend) ? ((*_offsets)[dtmp - dbegin]) : f.size());
                if (_readPtr <= mbegin) {
                    // We will only copy from the field ref once.
                    // If we have overlapping matches only the first one will be considered.
                    insertSeparators(mbegin, mend);
                }
                addHit(*qt, words);
            }
        }
        if ( ! Fast_UnicodeUtil::IsWordChar(*ditr++) ) {
            words++;
            for(; (ditr < drend) && ! Fast_UnicodeUtil::IsWordChar(*ditr) ; ++ditr );
        }
    }
    assert(_readPtr <= (f.data() + f.size()));
    // copy remaining
    size_t toCopy = f.size() - (_readPtr - f.data());
    copyToModified(toCopy);

    return words + 1; // we must also count the last word
}

size_t
UTF8SubstringSnippetModifier::matchTerm(const FieldRef & f, QueryTerm & qt)
{
    const cmptype_t * term;
    termsize_t tsz = qt.term(term);
    return matchTerms(f, tsz);
}

void
UTF8SubstringSnippetModifier::copyToModified(size_t n, bool skipSep)
{
    if (n == 0) {
        return;
    }
    if (skipSep) {
        for (const char * readEnd = _readPtr + n; _readPtr < readEnd; ++_readPtr) {
            if (!isSeparatorCharacter(*_readPtr)) {
                _modified->put(*_readPtr);
            }
        }
    } else {
        _modified->put(_readPtr, n);
        _readPtr += n;
    }
}

void
UTF8SubstringSnippetModifier::insertSeparators(const char * mbegin, const char * mend)
{
    copyToModified(mbegin - _readPtr);
    _modified->put(_unitSep);
    // skip separators such that the match is not splitted.
    copyToModified((mend - mbegin), true);
    _modified->put(_unitSep);
}

UTF8SubstringSnippetModifier::UTF8SubstringSnippetModifier() :
    UTF8StringFieldSearcherBase(),
    _modified(new CharBuffer(32)),
    _offsets(new std::vector<size_t>(32)),
    _readPtr(NULL),
    _unitSep(juniper::separators::unit_separator)
{
}

UTF8SubstringSnippetModifier::UTF8SubstringSnippetModifier(FieldIdT fId) :
    UTF8StringFieldSearcherBase(fId),
    _modified(new CharBuffer(32)),
    _offsets(new std::vector<size_t>(32)),
    _readPtr(NULL),
    _unitSep(juniper::separators::unit_separator)
{
}

UTF8SubstringSnippetModifier::UTF8SubstringSnippetModifier(FieldIdT fId,
                                                           const CharBuffer::SP & modBuf,
                                                           const SharedOffsetBuffer & offBuf) :
    UTF8StringFieldSearcherBase(fId),
    _modified(modBuf),
    _offsets(offBuf),
    _readPtr(NULL),
    _unitSep(juniper::separators::unit_separator)
{
}

UTF8SubstringSnippetModifier::~UTF8SubstringSnippetModifier() {}

}

