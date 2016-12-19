// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iosfwd>

namespace storage {
namespace memfile {

struct VdsDiskTool {
    static int run(int argc, const char * const * argv,
                   const std::string& rootPath,
                   std::ostream& out, std::ostream& err);
};

} // memfile
} // storage

