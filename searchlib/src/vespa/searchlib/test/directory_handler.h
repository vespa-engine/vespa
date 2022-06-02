// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <filesystem>

namespace search::test {

class DirectoryHandler
{
private:
    vespalib::string _mkdir;
    vespalib::string _rmdir;
    bool             _cleanup;

public:
    DirectoryHandler(const vespalib::string &mkdir)
        : DirectoryHandler(mkdir, mkdir)
    {
    }
    DirectoryHandler(const vespalib::string &mkdir,
                     const vespalib::string &rmdir)
        : _mkdir(mkdir),
          _rmdir(rmdir),
          _cleanup(true)
    {
        std::filesystem::create_directories(std::filesystem::path(_mkdir));
    }
    ~DirectoryHandler() {
        if (_cleanup) {
            std::filesystem::remove_all(std::filesystem::path(_rmdir));
        }
    }
    void cleanup(bool v) { _cleanup = v; }
    const  vespalib::string & getDir() const { return _mkdir; }
};

}
