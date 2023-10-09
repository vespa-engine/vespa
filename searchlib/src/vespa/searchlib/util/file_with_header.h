// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/fileheader.h>
#include <memory>

class FastOS_FileInterface;

namespace search {

/**
 * Class that encapsulates a file containing a generic file header, followed by binary data.
 *
 * After construction the file is positioned at the start of the binary data.
 * It's assumed that the file was written using FileSettings::DIRECTIO_ALIGNMENT.
 */
class FileWithHeader {
private:
    std::unique_ptr<FastOS_FileInterface> _file;
    vespalib::FileHeader _header;
    uint64_t _header_len;
    uint64_t _file_size;

public:
    FileWithHeader(std::unique_ptr<FastOS_FileInterface> file_in);
    ~FileWithHeader();
    FastOS_FileInterface& file() const { return *_file; }
    const vespalib::GenericHeader& header() const { return _header; }
    uint64_t file_size() const { return _file_size; }
    uint64_t data_size() const { return _file_size - _header_len; }

    bool valid() const;
    void rewind();
    void close();
};

}
