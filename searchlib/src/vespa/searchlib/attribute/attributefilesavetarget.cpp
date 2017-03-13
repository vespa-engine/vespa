// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.attributefilesavetarget");

#include "attributefilesavetarget.h"
#include "attributevector.h"
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/data/databuffer.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/vespalib/util/error.h>

using vespalib::getLastErrorString;

namespace search
{

using common::FileHeaderContext;


AttributeFileSaveTarget::
AttributeFileSaveTarget(const TuneFileAttributes &tuneFileAttributes,
                        const FileHeaderContext &fileHeaderContext)
    : IAttributeSaveTarget(),
      _datWriter(tuneFileAttributes, fileHeaderContext, _cfg,
                 "Attribute vector data file"),
      _idxWriter(tuneFileAttributes, fileHeaderContext, _cfg,
                 "Attribute vector idx file"),
      _weightWriter(tuneFileAttributes, fileHeaderContext, _cfg,
                    "Attribute vector weight file"),
      _udatWriter(tuneFileAttributes, fileHeaderContext, _cfg,
                  "Attribute vector unique data file")
{
}

AttributeFileSaveTarget::~AttributeFileSaveTarget() {
}

bool
AttributeFileSaveTarget::setup()
{
    const vespalib::string & baseFileName = _cfg.getFileName();
    vespalib::string datFileName(baseFileName + ".dat");
    if (!_datWriter.open(datFileName)) {
        return false;
    }
    if (_cfg.getEnumerated()) {
        vespalib::string udatFileName(baseFileName + ".udat");
        if (!_udatWriter.open(udatFileName)) {
            return false;
        }
    }
    if (_cfg.hasMultiValue()) {
        vespalib::string idxFileName(baseFileName + ".idx");
        if (!_idxWriter.open(idxFileName)) {
            return false;
        }
        if (_cfg.hasWeightedSetType()) {
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


} // namespace search

