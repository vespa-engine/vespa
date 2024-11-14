// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "file_with_header.h"
#include "file_settings.h"
#include "filesizecalculator.h"
#include <vespa/fastos/file.h>
#include <vespa/searchlib/util/disk_space_calculator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

namespace search {

namespace {

bool
extract_file_size(const vespalib::GenericHeader& header,
                  FastOS_FileInterface& file, uint64_t& logical_file_size)
{
    return FileSizeCalculator::extractFileSize(header, header.getSize(), file.GetFileName(), logical_file_size);
}

}

FileWithHeader::FileWithHeader(std::unique_ptr<FastOS_FileInterface> file_in)
    : _file(std::move(file_in)),
      _header(FileSettings::DIRECTIO_ALIGNMENT),
      _header_len(0),
      _file_size(0),
      _size_on_disk(0)
{
    if (valid()) {
        _header_len = _header.readFile(*_file);
        _file->SetPosition(_header_len);
        _file_size = _file->getSize();
        DiskSpaceCalculator calc;
        _size_on_disk = calc(_file_size);
        if (!extract_file_size(_header, *_file, _file_size)) {
            bool close_ok = _file->Close();
            assert(close_ok);
        }
    }
}

FileWithHeader::~FileWithHeader() = default;

bool
FileWithHeader::valid() const
{
    return _file && _file->IsOpened();
}

void
FileWithHeader::rewind()
{
    _file->SetPosition(_header_len);
}

void
FileWithHeader::close()
{
    bool close_ok = _file->Close();
    assert(close_ok);
}

}
