// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexdisklayout.h"
#include "index_disk_dir.h"
#include <sstream>

namespace searchcorespi::index {

const std::string
IndexDiskLayout::FlushDirPrefix = std::string("index.flush.");

const std::string
IndexDiskLayout::FusionDirPrefix = std::string("index.fusion.");

const std::string
IndexDiskLayout::SerialNumTag = std::string("Serial num");

IndexDiskLayout::IndexDiskLayout(const std::string &baseDir)
    : _baseDir(baseDir)
{
}

std::string
IndexDiskLayout::getFlushDir(uint32_t sourceId) const
{
    std::ostringstream ost;
    ost << _baseDir << "/" << FlushDirPrefix << sourceId;
    return ost.str();
}

std::string
IndexDiskLayout::getFusionDir(uint32_t sourceId) const
{
    std::ostringstream ost;
    ost << _baseDir << "/" << FusionDirPrefix << sourceId;
    return ost.str();
}

std::string
IndexDiskLayout::getSerialNumFileName(const std::string &dir)
{
    return dir + "/serial.dat";
}

std::string
IndexDiskLayout::getSchemaFileName(const std::string &dir)
{
    return dir + "/schema.txt";
}

std::string
IndexDiskLayout::getSelectorFileName(const std::string &dir)
{
    return dir + "/selector";
}

IndexDiskDir
IndexDiskLayout::get_index_disk_dir(const std::string& dir)
{
    auto name = dir.substr(dir.rfind('/') + 1);
    const std::string* prefix = nullptr;
    bool fusion = false;
    if (name.find(FlushDirPrefix) == 0) {
        prefix = &FlushDirPrefix;
    } else if (name.find(FusionDirPrefix) == 0) {
        prefix = &FusionDirPrefix;
        fusion = true;
    } else {
        return IndexDiskDir(); // invalid
    }
    std::istringstream ist(name.substr(prefix->size()));
    uint32_t id = 0;
    ist >> id;
    return IndexDiskDir(id, fusion); // invalid if id == 0u
}

}
