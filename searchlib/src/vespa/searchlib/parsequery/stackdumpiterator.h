// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "parse.h"
#include <vespa/vespalib/util/vespa_dll_local.h>
#include <vespa/searchlib/query/query_stack_iterator.h>
#include <memory>
#include <string>

namespace search {

/**
 * An iterator to be used on a buffer that is a stack dump
 * of a SimpleQueryStack.
 */
class SimpleQueryStackDumpIterator : public QueryStackIterator
{
private:
    /** Pointer to the start of the input buffer */
    const char *_buf;
    /** Pointer to just past the input buffer */
    const char *_bufEnd;
    /** Pointer to the position of the current item in the buffer */
    uint32_t    _currPos;
    /** Pointer to after the current item */
    uint32_t    _currEnd;

    VESPA_DLL_LOCAL std::string_view read_string_view(const char *&p);
    VESPA_DLL_LOCAL uint64_t readCompressedPositiveInt(const char *&p);
    VESPA_DLL_LOCAL int64_t readCompressedInt(const char *&p);
    template <typename T>
    VESPA_DLL_LOCAL T read_value(const char*& p);
    VESPA_DLL_LOCAL void readPredicate(const char *&p);
    VESPA_DLL_LOCAL void readNN(const char *&p);
    VESPA_DLL_LOCAL void readComplexTerm(const char *& p);
    VESPA_DLL_LOCAL void readFuzzy(const char *&p);
    VESPA_DLL_LOCAL void read_string_in(const char*& p);
    VESPA_DLL_LOCAL void read_numeric_in(const char*& p);
    VESPA_DLL_LOCAL bool readNext();
public:
    /**
     * Make an iterator on a buffer. To get the first item, next must be called.
     */
    explicit SimpleQueryStackDumpIterator(std::string_view buf);
    SimpleQueryStackDumpIterator(const SimpleQueryStackDumpIterator &) = delete;
    SimpleQueryStackDumpIterator& operator=(const SimpleQueryStackDumpIterator &) = delete;
    ~SimpleQueryStackDumpIterator();

    std::string_view getStack() const noexcept override { return std::string_view(_buf, _bufEnd - _buf); }
    size_t getPosition() const noexcept override { return _currPos; }

    /**
     * Moves to the next item in the buffer.
     *
     * @return true if there is a new item, false if there are no more items
     * or if there was errors in extracting the next item.
     */
    bool next() override;
};

}
