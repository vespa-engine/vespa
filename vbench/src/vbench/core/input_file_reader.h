// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "string.h"
#include "taintable.h"
#include "line_reader.h"
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/input_reader.h>

namespace vbench {

/**
 * Read non-empty lines from an input file. This class is implemented
 * in terms of the MappedFileInput and LineReader classes.
 **/
class InputFileReader : public Taintable
{
private:
    vespalib::MappedFileInput _file;
    LineReader                _lines;
    Taint                     _taint;

public:
    InputFileReader(const string &name);
    ~InputFileReader();

    /**
     * Read a single line from the input file and put it into
     * 'dst'. Empty lines and '\r' directly before '\n' will be
     * ignored. Lines are terminated by '\n' or EOF.
     *
     * @return true if dst is non-empty
     * @param dst place to put read line
     **/
    bool readLine(string &dst);

    const Taint &tainted() const override { return _taint; }
};

} // namespace vbench

