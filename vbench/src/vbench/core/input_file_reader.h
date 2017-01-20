// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string.h"
#include "taint.h"
#include "mapped_file_input.h"
#include "byte_input.h"
#include "line_reader.h"

namespace vbench {

/**
 * Read non-empty lines from an input file. This class is implemented
 * in terms of the MappedFileInput and LineReader classes.
 **/
class InputFileReader : public Taintable
{
private:
    MappedFileInput _file;
    LineReader      _lines;

public:
    InputFileReader(const string &name)
        : _file(name), _lines(_file) {}

    /**
     * Read a single line from the input file and put it into
     * 'dst'. Empty lines and '\r' directly before '\n' will be
     * ignored. Lines are terminated by '\n' or EOF.
     *
     * @return true if dst is non-empty
     * @param dst place to put read line
     **/
    bool readLine(string &dst);

    virtual const Taint &tainted() const { return _file.tainted(); }
};

} // namespace vbench

