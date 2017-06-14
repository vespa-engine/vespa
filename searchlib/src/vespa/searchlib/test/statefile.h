// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/stllike/string.h>
#include <vector>

namespace search {

class StateFile;

namespace test::statefile {

vespalib::string readState(StateFile &sf);
std::vector<vespalib::string> readHistory(const char *name);

}
}
