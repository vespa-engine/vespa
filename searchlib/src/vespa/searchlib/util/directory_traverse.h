// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <filesystem>

namespace search {

/*
 * Class used to get size of directory tree on disk.
 */
class DirectoryTraverse
{
private:
    std::filesystem::path _path;
public:
    uint64_t GetTreeSize(); // Returns size of directory in bytes
    explicit DirectoryTraverse(const std::filesystem::path& path);
    ~DirectoryTraverse();
    static uint64_t get_tree_size(const std::filesystem::path& path);
};

} // namespace search
