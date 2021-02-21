// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 *
 * $Id$

 *
 * String utilities
 *
 */

#include "stringutil.h"
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/stllike/asciistream.h>

#include <iomanip>
#include <sstream>
#include <vector>
#include <cassert>
#include <algorithm>

using vespalib::IllegalArgumentException;

namespace document {

namespace {
    char toHex(uint32_t val) {
        return (val < 10 ? '0' + val : 'a' + (val - 10));
    }
}

class ReplacementCharacters {
public:
    ReplacementCharacters();
    static int  needEscape(unsigned char c) { return _needEscape[c]; }
    static char getChar1(unsigned char c)   { return _replacement1[c]; }
    static char getChar2(unsigned char c)   { return _replacement2[c]; }
private:
    static char _needEscape[256];
    static char _replacement1[256];
    static char _replacement2[256];
};

char ReplacementCharacters::_needEscape[256];
char ReplacementCharacters::_replacement1[256];
char ReplacementCharacters::_replacement2[256];

ReplacementCharacters::ReplacementCharacters()
{
    for(size_t i(0); i < sizeof(_needEscape); i++) {
        const char c = i;
        if (c == '"') {
            _needEscape[i] = 1;
            _replacement1[i] = '\\';
            _replacement2[i] = '"';
        } else if (c == '\\') {
            _needEscape[i] = 1;
            _replacement1[i] = '\\';
            _replacement2[i] = '\\';
        } else if (c == '\t') {
            _needEscape[i] = 1;
            _replacement1[i] = '\\';
            _replacement2[i] = 't';
        } else if (c == '\n') {
            _needEscape[i] = 1;
            _replacement1[i] = '\\';
            _replacement2[i] = 'n';
        } else if (c == '\r') {
            _needEscape[i] = 1;
            _replacement1[i] = '\\';
            _replacement2[i] = 'r';
        } else if (c == '\f') {
            _needEscape[i] = 1;
            _replacement1[i] = '\\';
            _replacement2[i] = 'f';
        } else if ((c < 32) || (c > 126)) {
            _needEscape[i] = 3;
            _replacement1[i] = toHex((c >> 4) & 0xF);
            _replacement2[i] = toHex(c & 0xF);
        } else {
            _needEscape[i] = 0;
            _replacement1[i] = c;
            _replacement2[i] = c;
        }
    }
}

static ReplacementCharacters _G_ForceInitialisation;

const vespalib::string & StringUtil::escape(const vespalib::string & source, vespalib::string & destination,
                                       char delimiter)
{
    size_t escapeCount(0);
    for(size_t i(0), m(source.size()); i < m; i++) {
        if (source[i] == delimiter) {
            escapeCount += 3;
        } else {
            escapeCount += ReplacementCharacters::needEscape(source[i]);
        }
    }
    if (escapeCount > 0) {
        std::vector<char> dst;
        dst.reserve(source.size() + escapeCount);
        for(size_t i(0), m(source.size()); i < m; i++) {
            const char c = source[i];
            if (c == delimiter) {
                dst.push_back('\\');
                dst.push_back('x');
                dst.push_back(toHex((c >> 4) & 0xF));
                dst.push_back(toHex(c & 0xF));
            } else {
                int needEscape = ReplacementCharacters::needEscape(c);
                if (needEscape == 0) {
                    dst.push_back(c);
                } else {
                    if (needEscape == 3) {
                        dst.push_back('\\');
                        dst.push_back('x');
                    }
                    dst.push_back(ReplacementCharacters::getChar1(c));
                    dst.push_back(ReplacementCharacters::getChar2(c));
                }
            }
        }
        destination.assign(&dst[0], dst.size());
        return destination;
    }
    return source;
}

vespalib::string StringUtil::unescape(vespalib::stringref source)
{
    vespalib::asciistream ost;
    for (unsigned int i=0; i<source.size(); ++i) {
        if (source[i] != '\\') { ost << source[i]; continue; }
        // Here we know we have an escape
        if (i+1 == source.size()) {
            throw IllegalArgumentException("Found backslash at end of input",
                                           VESPA_STRLOC);
        }
        if (source[i+1] != 'x') {
            switch (source[i+1]) {
            case '\\': ost << '\\'; break;
            case '"': ost << '"'; break;
            case 't': ost << '\t'; break;
            case 'n': ost << '\n'; break;
            case 'r': ost << '\r'; break;
            case 'f': ost << '\f'; break;
            default:
                throw IllegalArgumentException(
                        vespalib::make_string("Illegal escape sequence \\%c found", source[i+1]), VESPA_STRLOC);
            }
            ++i;
            continue;
        }
        // Only \x## sequences left..
        if (i+3 >= source.size()) {
            throw IllegalArgumentException("Found \\x at end of input",
                                           VESPA_STRLOC);
        }
        vespalib::string hexdigits = source.substr(i+2, 2);
        char* endp(0);
        ost << static_cast<char>(strtol(hexdigits.c_str(), &endp, 16));
        if (*endp) {
            throw IllegalArgumentException("Value "+hexdigits
                                           + " is not a two digit hexadecimal number", VESPA_STRLOC);
        }
        i+=3;
    }
    return ost.str();
}

void StringUtil::
printAsHex(std::ostream& output, const void* source, unsigned int size,
           unsigned int columnwidth, bool inlinePrintables,
           const std::string& indent)
{
    assert(columnwidth > 0);
    unsigned char wildChar = '.';
    const unsigned char* start = reinterpret_cast<const unsigned char*>(source);
    uint32_t posWidth = 1;
    for (uint32_t i=size; i>9; i /= 10) { ++posWidth; }
    std::vector<unsigned char> printables(static_cast<size_t>(columnwidth) + 1);
    printables[columnwidth] = '\0';
    for (unsigned int i=0; i<size; i += columnwidth) {
        std::ostringstream ost;
        if (i != 0) ost << "\n" << indent;
        ost << std::dec << std::setw(posWidth) << i << ":";
        bool nonNull = false;
        for (unsigned int j=0; j<columnwidth; ++j)
        {
            if (i+j >= size) {
                ost << "   ";
                printables[j] = '\0'; // Avoid adding extra chars.
            } else {
                ost << " " << std::setw(2);
                bool printable = (start[i+j] >= 33 && start[i+j] <= 126);
                if (inlinePrintables && printable) {
                    ost << std::setfill(' ') << start[i+j];
                } else {
                    ost << std::hex << std::setfill('0')
                        << ((unsigned int) start[i+j]);
                    printables[j] = (printable ? start[i+j] : wildChar);
                }
                nonNull |= (start[i+j] != 0);
            }
        }
        if (nonNull) {
            output << ost.str();
            if (!inlinePrintables) {
                output << " " << &printables[0];
            }
        }
    }
}


} // namespace document
