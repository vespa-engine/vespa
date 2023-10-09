// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "methods.h"
#include <iostream>

namespace methods {

const Method methods[] = {
    { "cache", "listCachedConfig", 0 },
    { "dumpcache", "dumpCache", 1 }, // filename
    { "getConfig", "getConfig", 7 }, // defName defVersion defMD5 configid configXXhash64 timestamp timeout
    { "getmode", "getMode", 0 },
    { "invalidatecache", "invalidateCache", 0 },
    { "cachefull", "listCachedConfigFull", 0 },
    { "sources", "listSourceConnections", 0 },
    { "statistics", "printStatistics", 0 },
    { "setmode", "setMode", 1 }, // { default | memorycache }
    { "updatesources", "updateSources", 1 },
    { 0, 0, 0}
};

const Method find(const vespalib::string &name) {
    for (size_t i = 0; methods[i].shortName != 0; ++i) {
        if (name == methods[i].shortName) {
            return methods[i];
        }
    }
    Method rv = { name.c_str(), name.c_str(), 0 };
    return rv;
}

void dump() {
    std::cerr << "    ";
    size_t i = 0;
    for (;;) {
        std::cerr << methods[i++].shortName;
        if (methods[i].shortName == 0) {
            break;
        }
        std::cerr << ",";
    }
    std::cerr << std::endl;
}

};
