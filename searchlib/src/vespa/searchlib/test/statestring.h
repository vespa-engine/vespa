// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search::test::statestring {

void normalizeTimestamp(vespalib::string &s);
void normalizeAddr(vespalib::string &s, void *addr);
void normalizeTimestamps(std::vector<vespalib::string> &sv);
void normalizeAddrs(std::vector<vespalib::string> &sv, void *addr);

}