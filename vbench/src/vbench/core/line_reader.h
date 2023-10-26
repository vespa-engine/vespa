// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/input_reader.h>
#include "string.h"

namespace vbench {

using Input = vespalib::Input;

/**
 * Concrete utility class used to read individual lines of text from
 * an underlying input. This class is implemented in terms of the
 * ByteInput class.
 **/
class LineReader
{
public:
    using InputReader = vespalib::InputReader;
private:
    InputReader _input;

public:
    /**
     * Wrap an Input to read one line at a time.
     *
     * @param input the underlying Input
     **/
    LineReader(Input &input);

    /**
     * Read the next line of input. Lines are separated by '\n'. '\r'
     * appearing directly in from of '\n' will be stripped. Empty
     * lines will be returned.
     *
     * @return true if a line could be read,
     *         false if no more data was available
     * @param dst where to store the line that was read
     **/
    bool readLine(string &dst);
};

} // namespace vbench

