// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "input_file_reader.h"

namespace vbench {

bool
InputFileReader::readLine(string &dst)
{
    while (_lines.readLine(dst) && dst.empty()) {
        // skip empty lines
    }
    return !dst.empty();
}

} // namespace vbench
