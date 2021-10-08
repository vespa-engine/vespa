// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexdisklayout.h"
#include <sstream>

namespace searchcorespi::index {

const vespalib::string
IndexDiskLayout::FlushDirPrefix = vespalib::string("index.flush.");

const vespalib::string
IndexDiskLayout::FusionDirPrefix = vespalib::string("index.fusion.");

const vespalib::string
IndexDiskLayout::SerialNumTag = vespalib::string("Serial num");

IndexDiskLayout::IndexDiskLayout(const vespalib::string &baseDir)
    : _baseDir(baseDir)
{
}

vespalib::string
IndexDiskLayout::getFlushDir(uint32_t sourceId) const
{
    std::ostringstream ost;
    ost << _baseDir << "/" << FlushDirPrefix << sourceId;
    return ost.str();
}

vespalib::string
IndexDiskLayout::getFusionDir(uint32_t sourceId) const
{
    std::ostringstream ost;
    ost << _baseDir << "/" << FusionDirPrefix << sourceId;
    return ost.str();
}

vespalib::string
IndexDiskLayout::getSerialNumFileName(const vespalib::string &dir)
{
    return dir + "/serial.dat";
}

vespalib::string
IndexDiskLayout::getSchemaFileName(const vespalib::string &dir)
{
    return dir + "/schema.txt";
}

vespalib::string
IndexDiskLayout::getSelectorFileName(const vespalib::string &dir)
{
    return dir + "/selector";
}

}
