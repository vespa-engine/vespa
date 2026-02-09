// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "directory_traverse.h"
#include "disk_space_calculator.h"
#include <vespa/vespalib/util/size_literals.h>
#include <filesystem>
#include <system_error>

namespace search {

namespace fs = std::filesystem;

namespace {

uint64_t
try_get_tree_size(const std::string& base_dir)
{
    fs::path path(base_dir);
    std::error_code ec;
    fs::recursive_directory_iterator dir_itr(path, fs::directory_options::skip_permission_denied, ec);
    if (ec) {
        return 0;
    }

    uint64_t total_size = DiskSpaceCalculator::directory_placeholder_size();
    DiskSpaceCalculator calc;
    for (const auto &elem : dir_itr) {
        if (elem.is_symlink()) {
            total_size += DiskSpaceCalculator::symlink_placeholder_size();
        } else if (elem.is_directory()) {
            total_size += DiskSpaceCalculator::directory_placeholder_size();
        } else if (elem.is_regular_file()) {
            const auto size = elem.file_size(ec);
            if (!ec) {
                total_size += calc(size);
            }
        }
    }
    return total_size;
}

}

DirectoryTraverse::DirectoryTraverse(const std::string& base_dir)
    : _base_dir(base_dir)
{
}

DirectoryTraverse::~DirectoryTraverse() = default;

uint64_t
DirectoryTraverse::GetTreeSize()
{
    // Since try_get_tree_size may throw on concurrent directory
    // modifications, immediately retry a bounded number of times if this
    // happens.  Number of retries chosen randomly by counting fingers.
    for (int i = 0; i < 10; ++i) {
        try {
            return try_get_tree_size(_base_dir);
        } catch (const fs::filesystem_error&) {
            // Go around for another spin that hopefully won't race.
        }
    }
    return 0;
}

uint64_t
DirectoryTraverse::get_tree_size(const std::string& base_dir)
{
    DirectoryTraverse traverse(base_dir);
    return traverse.GetTreeSize();
}

} // namespace search
