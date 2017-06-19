// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "vdsdisktool.h"
#include <vespa/defaults.h>
#include <vespa/fastos/app.h>
#include <iostream>

namespace {
    struct DiskApp : public FastOS_Application {
        int Main() override {
            try {
                std::string dir = vespa::Defaults::underVespaHome("var/db/vespa/vds");
                return storage::memfile::VdsDiskTool::run(
                        _argc, _argv, dir.c_str(),
                        std::cout, std::cerr);
            } catch (std::exception& e) {
                std::cerr << "Application aborted with exception:\n" << e.what()
                          << "\n";
                return 1;
            }
        }
    };
} // anonymous

int main(int argc, char **argv) {
    DiskApp app;
    return app.Entry(argc, argv);
}

