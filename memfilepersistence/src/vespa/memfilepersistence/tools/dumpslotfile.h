// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <iosfwd>

namespace config {
    class ConfigUri;
}

namespace storage {
namespace memfile {

struct SlotFileDumper {
    static int dump(int argc, const char * const * argv,
                    config::ConfigUri& config,
                    std::ostream& out, std::ostream& err);
};

} // memfile
} // storage

