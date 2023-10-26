// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "line_reader.h"

namespace vbench {

namespace {
void stripCR(string &dst) {
    if (!dst.empty() && dst[dst.size() - 1] == '\r') {
        dst.resize(dst.size() - 1);
    }
}
} // namespace vbench::<unnamed>

LineReader::LineReader(Input &input)
    : _input(input)
{
}

bool
LineReader::readLine(string &dst)
{
    dst.clear();
    for (char c = _input.read(); !_input.failed(); c = _input.read()) {
        if (c != '\n') {
            dst.push_back(c);
        } else {
            stripCR(dst);
            return true;
        }
    }
    return !dst.empty();
}

} // namespace vbench
