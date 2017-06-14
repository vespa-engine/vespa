// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dumpslotfile.h"
#include <vespa/config/subscription/configuri.h>
#include <vespa/fastos/app.h>
#include <iostream>

namespace {

struct DumpSlotFileApp : public FastOS_Application {
    int Main() override {
        try{
            config::ConfigUri config("");
            return storage::memfile::SlotFileDumper::dump(
                    _argc, _argv, config, std::cout, std::cerr);
        } catch (std::exception& e) {
            std::cerr << "Aborting due to exception:\n" << e.what() << "\n";
            return 1;
        }
    }
};

} // anonymous

int main(int argc, char **argv) {
    DumpSlotFileApp app;
    return app.Entry(argc, argv);
}
