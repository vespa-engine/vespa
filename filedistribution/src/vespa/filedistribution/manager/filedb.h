// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <boost/filesystem/path.hpp>
#include "filedbmodel.h"

namespace filedistribution {

class FileDB {
    boost::filesystem::path _dbPath;
public:
    FileDB(boost::filesystem::path dbPath);
    DirectoryGuard::UP getGuard() { return std::make_unique<DirectoryGuard>(_dbPath); }
    void add(boost::filesystem::path original, const std::string& name);
};

} //namespace filedistribution

