// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefilesavetarget.h"
#include "attributevector.h"
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/util/error.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attributefilesavetarget");

using vespalib::getLastErrorString;
using vespalib::IllegalArgumentException;

namespace search {

using common::FileHeaderContext;


AttributeFileSaveTarget::
AttributeFileSaveTarget(const TuneFileAttributes& tune_file,
                        const FileHeaderContext& file_header_ctx)
    : IAttributeSaveTarget(),
      _tune_file(tune_file),
      _file_header_ctx(file_header_ctx),
      _datWriter(tune_file, file_header_ctx, _header, "Attribute vector data file"),
      _idxWriter(tune_file, file_header_ctx, _header, "Attribute vector idx file"),
      _weightWriter(tune_file, file_header_ctx, _header, "Attribute vector weight file"),
      _udatWriter(tune_file, file_header_ctx, _header, "Attribute vector unique data file"),
      _writers()
{
}

AttributeFileSaveTarget::~AttributeFileSaveTarget() = default;

bool
AttributeFileSaveTarget::setup()
{
    const vespalib::string & baseFileName = _header.getFileName();
    vespalib::string datFileName(baseFileName + ".dat");
    if (!_datWriter.open(datFileName)) {
        return false;
    }
    if (_header.getEnumerated()) {
        vespalib::string udatFileName(baseFileName + ".udat");
        if (!_udatWriter.open(udatFileName)) {
            return false;
        }
    }
    if (_header.hasMultiValue()) {
        vespalib::string idxFileName(baseFileName + ".idx");
        if (!_idxWriter.open(idxFileName)) {
            return false;
        }
        if (_header.hasWeightedSetType()) {
            vespalib::string weightFileName(baseFileName + ".weight");
            if (!_weightWriter.open(weightFileName)) {
                return false;
            }
        }
    }
    return true;
}

void
AttributeFileSaveTarget::close()
{
    _datWriter.close();
    _udatWriter.close();
    _idxWriter.close();
    _weightWriter.close();
    for (auto& writer : _writers) {
        writer.second->close();
    }
}

IAttributeFileWriter &
AttributeFileSaveTarget::datWriter()
{
    return _datWriter;
}

IAttributeFileWriter &
AttributeFileSaveTarget::idxWriter()
{
    return _idxWriter;
}

IAttributeFileWriter &
AttributeFileSaveTarget::weightWriter()
{
    return _weightWriter;
}

IAttributeFileWriter &
AttributeFileSaveTarget::udatWriter()
{
    return _udatWriter;
}

bool
AttributeFileSaveTarget::setup_writer(const vespalib::string& file_suffix,
                                      const vespalib::string& desc)
{
    vespalib::string file_name(_header.getFileName() + "." + file_suffix);
    auto writer = std::make_unique<AttributeFileWriter>(_tune_file, _file_header_ctx,
                                                        _header, desc);
    if (!writer->open(file_name)) {
        return false;
    }
    auto itr = _writers.find(file_suffix);
    if (itr != _writers.end()) {
        return false;
    }
    _writers.insert(std::make_pair(file_suffix, std::move(writer)));
    return true;
}

IAttributeFileWriter&
AttributeFileSaveTarget::get_writer(const vespalib::string& file_suffix)
{
    auto itr = _writers.find(file_suffix);
    if (itr == _writers.end()) {
        throw IllegalArgumentException("File writer with suffix '" + file_suffix + "' does not exist");
    }
    return *itr->second;
}

} // namespace search

