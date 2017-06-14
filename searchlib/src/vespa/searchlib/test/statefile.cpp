// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "statefile.h"
#include <vespa/searchlib/util/statefile.h>
#include <iostream>
#include <fstream>
#include <string>

namespace search::test::statefile {

vespalib::string
readState(StateFile &sf)
{
    std::vector<char> buf;
    sf.readState(buf);
    return vespalib::string(buf.begin(), buf.end());
}

std::vector<vespalib::string>
readHistory(const char *name)
{
    std::vector<vespalib::string> res;
    std::ifstream is(name);
    std::string line;
    while (!is.eof()) {
        std::getline(is, line);
        if (is.eof() && line.empty()) {
            break;
        }
        res.push_back(line + "\n");
    }
    return res;
}

}
