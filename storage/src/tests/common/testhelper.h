// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/vdstestlib/config/dirconfig.h>
#include <fstream>
#include <sstream>

namespace storage {

void addFileConfig(vdstestlib::DirConfig& dc,
                   const std::string& configDefName,
                   const std::string& fileName);


void addStorageDistributionConfig(vdstestlib::DirConfig& dc);

vdstestlib::DirConfig getStandardConfig(bool storagenode, const std::string & rootFolder = "todo-make-unique");

std::string getRootFolder(vdstestlib::DirConfig & dc);

void addSlobrokConfig(vdstestlib::DirConfig& dc,
                      const mbus::Slobrok& slobrok);

// Class used to print start and end of test. Enable debug when you want to see
// which test creates what output or where we get stuck
struct TestName {
    std::string name;
    TestName(const std::string& n);
    ~TestName();
};

} // storage

