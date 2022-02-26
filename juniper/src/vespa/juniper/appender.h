// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/hdr_abort.h>
#include <cstddef>
#include <vector>

class SummaryConfig;

namespace juniper {

class Appender
{
private:
    const SummaryConfig *_sumconf;
    bool                 _escape_markup;
    bool                 _preserve_white_space;
    bool                 _last_was_space;
    size_t               _char_len;

    void append(std::vector<char> & s, char c);

public:
    Appender(const SummaryConfig *sumconf);

    size_t charLen() const { return _char_len; }

    void append(std::vector<char>& s, const char* ds, int length);
};

} // end namespace juniper

