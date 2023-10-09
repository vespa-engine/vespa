// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "input_file_reader.h"

namespace vbench {

InputFileReader::InputFileReader(const string &name)
    : _file(name),
      _lines(_file),
      _taint()
{
    if (!_file.valid()) {
        _taint.reset(strfmt("could not open file: %s", name.c_str()));
    }
}

InputFileReader::~InputFileReader() {}

bool
InputFileReader::readLine(string &dst)
{
    while (_lines.readLine(dst) && dst.empty()) {
        // skip empty lines
    }
    return !dst.empty();
}

} // namespace vbench
