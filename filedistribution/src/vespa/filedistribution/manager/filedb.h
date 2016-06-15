// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>
#include <boost/filesystem/path.hpp>

namespace filedistribution {

class FileDB {
    boost::filesystem::path _dbPath;
    int _fd;
public:
    FileDB(boost::filesystem::path dbPath);
    void add(boost::filesystem::path original, const std::string& name);
};

} //namespace filedistribution

