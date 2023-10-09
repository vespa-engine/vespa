// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <fstream>
#include <sstream>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/vdstestlib/config/dirconfig.h>

namespace storage {

void addFileConfig(vdstestlib::DirConfig& dc,
                   const std::string& configDefName,
                   const std::string& fileName);


void addStorageDistributionConfig(vdstestlib::DirConfig& dc);

vdstestlib::DirConfig getStandardConfig(bool storagenode);

void addSlobrokConfig(vdstestlib::DirConfig& dc,
                      const mbus::Slobrok& slobrok);

} // storage

