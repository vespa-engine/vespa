// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "routingspec.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

RoutingSpec::RoutingSpec() noexcept = default;
RoutingSpec::RoutingSpec(const RoutingSpec &) = default;
RoutingSpec::RoutingSpec(RoutingSpec &&) noexcept = default;
RoutingSpec & RoutingSpec::operator=(RoutingSpec &&) noexcept = default;
RoutingSpec::~RoutingSpec() = default;

RoutingSpec &
RoutingSpec::addTable(RoutingTableSpec && table) & {
    _tables.emplace_back(std::move(table));
    return *this;
}

RoutingSpec &&
RoutingSpec::addTable(RoutingTableSpec && table) && {
    _tables.emplace_back(std::move(table));
    return std::move(*this);
}

string
RoutingSpec::toConfigString(const string &input)
{
    string ret;
    ret.append("\"");
    for (char i : input) {
        if (i == '\\') {
            ret.append("\\\\");
        } else if (i == '"') {
            ret.append("\\\"");
        } else if (i == '\n') {
            ret.append("\\n");
        } else if (i == 0) {
            ret.append("\\x00");
        } else {
            ret += i;
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
