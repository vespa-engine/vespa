// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <filesystem>
#include <string>

namespace search::test {

class DirectoryHandler
{
private:
    std::string _mkdir;
    std::string _rmdir;
    bool             _cleanup;

public:
    DirectoryHandler(const std::string &mkdir)
        : DirectoryHandler(mkdir, mkdir)
    {
    }
    DirectoryHandler(const std::string &mkdir,
                     const std::string &rmdir)
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
    const  std::string & getDir() const { return _mkdir; }
};

}
