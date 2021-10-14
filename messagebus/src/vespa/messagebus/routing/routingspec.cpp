// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routingspec.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

RoutingSpec::RoutingSpec() :
    _tables()
{
    // empty
}

RoutingTableSpec
RoutingSpec::removeTable(uint32_t i)
{
    RoutingTableSpec ret = _tables[i];
    _tables.erase(_tables.begin() + i);
    return ret;
}

string
RoutingSpec::toConfigString(const string &input)
{
    string ret;
    ret.append("\"");
    for (uint32_t i = 0, len = input.size(); i < len; ++i) {
        if (input[i] == '\\') {
            ret.append("\\\\");
        } else if (input[i] == '"') {
            ret.append("\\\"");
        } else if (input[i] == '\n') {
            ret.append("\\n");
        } else if (input[i] == 0) {
            ret.append("\\x00");
        } else {
            ret += input[i];
        }
    }
    ret.append("\"");
    return ret;
}

void
RoutingSpec::toConfig(string &cfg, const string &prefix) const
{
    uint32_t numTables = _tables.size();
    if (numTables > 0) {
        cfg.append(prefix).append("routingtable[").append(make_string("%d", numTables)).append("]\n");
        for (uint32_t i = 0; i < numTables; ++i) {
            _tables[i].toConfig(cfg, make_string("%sroutingtable[%d].", prefix.c_str(), i));
        }
    }
}

string
RoutingSpec::toString() const
{
    string ret = "";
    toConfig(ret, "");
    return ret;
}

bool
RoutingSpec::operator==(const RoutingSpec &rhs) const
{
    if (_tables.size() != rhs._tables.size()) {
        return false;
    }
    for (uint32_t i = 0, len = _tables.size(); i < len; ++i) {
        if (_tables[i] != rhs._tables[i]) {
            return false;
        }
    }
    return true;
}

} // namespace mbus
