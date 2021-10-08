// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "kernelmetrictool.h"
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <vespa/vespalib/util/exceptions.h>
#include <cctype>

namespace storage {
namespace kernelmetrictool {


vespalib::string readFile(const char* fileName) {
    return vespalib::asciistream::createFromDevice(fileName).str();
}

vespalib::string stripWhitespace(const vespalib::string& s) {
    vespalib::string::size_type start(0);
    vespalib::string::size_type stop(s.size() - 1);
    while (true) {
        if (start == s.size()) return vespalib::string("");
        if (!std::isspace(s[start])) break;
        ++start;
    }
    while (true) {
        if (!std::isspace(s[stop])) break;
        --stop;
    }
    return s.substr(start, stop - start + 1);
}

vespalib::string getLine(vespalib::stringref key,
                         vespalib::stringref content)
{
    vespalib::string::size_type start(0);
    vespalib::string::size_type stop(content.find('\n'));
    while (true) {
        bool last = (stop == vespalib::string::npos);
        vespalib::stringref line(content.substr(start, stop - start));
        for (uint32_t i=0, n=line.size(); i<n; ++i) {
            if (std::isspace(line[i])) {
                vespalib::stringref s(line.substr(0, i));
                if (s == key) return line;
            }
        }
        if (last) break;
        start = stop + 1;
        stop = content.find('\n', start);
    }
    return "";
}

vespalib::string getToken(uint32_t index, const vespalib::string& line) {
    vespalib::StringTokenizer st(line, " \t\n", "");
    st.removeEmptyTokens();
    return (index >= st.size() ? "" : st[index]);
}

uint32_t getTokenCount(const vespalib::string& line) {
    vespalib::StringTokenizer st(line, " \t\n", "");
    st.removeEmptyTokens();
    return st.size();
}

uint64_t toLong(vespalib::stringref s, int base) {
    char* endptr;
    // FIXME C++17 range-safe from_chars() instead of strtoull()
    uint64_t result(strtoull(s.data(), &endptr, base));
    if ((s.data() + s.size()) != endptr) {
        throw vespalib::IllegalArgumentException("Parsing '" + s + "' as a long.");
    }
    return result;
}
}
} /* namespace storage */
