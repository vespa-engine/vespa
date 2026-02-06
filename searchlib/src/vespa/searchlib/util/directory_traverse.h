// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <string>

namespace search {

/*
 * Class used to get size of directory tree on disk.
 */
class DirectoryTraverse
{
private:
    std::string _base_dir;
public:
    uint64_t GetTreeSize(); // Returns size of directory in bytes
    explicit DirectoryTraverse(const std::string& base_dir);
    ~DirectoryTraverse();
    static uint64_t get_tree_size(const std::string& base_dir);
};

} // namespace search
