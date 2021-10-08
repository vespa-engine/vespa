// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "utf8stringfieldsearcherbase.h"
#include <vespa/vsm/common/charbuffer.h>

namespace vsm {

typedef std::shared_ptr<std::vector<size_t> > SharedOffsetBuffer;

/**
 * This class does substring searches the same way as UTF8SubStringFieldSearcher.
 * While matching the query term(s) against the field reference it builds a modified
 * buffer based on the field reference where the only difference is that unit separators
 * are inserted before and after a match. These extra unit separators make it possible
 * to highlight a substring match when later generating snippets.
 **/
class UTF8SubstringSnippetModifier : public UTF8StringFieldSearcherBase
{
private:
    CharBuffer::SP      _modified; // buffer to write the modified field value
    SharedOffsetBuffer  _offsets;  // for each character in _buf we have an offset into the utf8 buffer (field reference)
    const char        * _readPtr;  // buffer to read from (field reference)
    char                _unitSep;  // the unit separator character to use

    virtual size_t matchTerm(const FieldRef & f, search::streaming::QueryTerm & qt) override;
    virtual size_t matchTerms(const FieldRef & f, const size_t shortestTerm) override;

    /**
     * Copies n bytes from the field reference to the modified buffer and updates the read pointer.
     * Separator characters from the field reference can be skipped.
     * This is to avoid that a match is splitted by separator characters from the original field reference.
     *
     * @param n the number of bytes to copy.
     * @param skipSep whether we should skip separator characters from the field reference.
     **/
    void copyToModified(size_t n, bool skipSep = false);

    /**
     * Copies from the field reference to the modified buffer and inserts unit separators for a match
     * starting at mbegin (in the field reference) and ending at mend (in the field reference).
     * A unit separator is inserted before and after the match.
     *
     * @param mbegin the beginning of the match.
     * @param mend the end of the match.
     **/
    void insertSeparators(const char * mbegin, const char * mend);

public:
    typedef std::shared_ptr<UTF8SubstringSnippetModifier> SP;

    std::unique_ptr<FieldSearcher> duplicate() const override;

    UTF8SubstringSnippetModifier();
    UTF8SubstringSnippetModifier(FieldIdT fId);
    ~UTF8SubstringSnippetModifier();

    /**
     * Creates a new instance.
     *
     * @param fId the field id to operate on.
     * @param modBuf the shared buffer used to store the modified field value.
     * @param offBuf the shared buffer used to store the offsets into the field reference.
     **/
    UTF8SubstringSnippetModifier(FieldIdT fId, const CharBuffer::SP & modBuf, const SharedOffsetBuffer & offBuf);

    const CharBuffer & getModifiedBuf() const { return *_modified; }
    const search::streaming::QueryTermList & getQueryTerms() const { return _qtl; }
};

}

