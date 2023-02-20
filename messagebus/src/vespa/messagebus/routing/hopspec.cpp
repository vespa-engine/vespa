// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "routingspec.h"
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::make_string;

namespace mbus {

HopSpec::HopSpec(const string &name, const string &selector) :
    _name(name),
    _selector(selector),
    _recipients(),
    _ignoreResult(false)
{ }

HopSpec::HopSpec(const HopSpec & rhs) = default;
HopSpec & HopSpec::operator=(const HopSpec & rhs) = default;
HopSpec::HopSpec(HopSpec && rhs) noexcept = default;
HopSpec & HopSpec::operator=(HopSpec && rhs) noexcept = default;
HopSpec::~HopSpec() = default;

HopSpec &
HopSpec::addRecipient(const string &recipient) & {
    _recipients.push_back(recipient);
    return *this;
}

HopSpec &&
HopSpec::addRecipient(const string &recipient) && {
    _recipients.push_back(recipient);
    return std::move(*this);
}

void
HopSpec::toConfig(string &cfg, const string &prefix) const
{
    cfg.append(prefix).append("name ").append(RoutingSpec::toConfigString(_name)).append("\n");
    cfg.append(prefix).append("selector ").append(RoutingSpec::toConfigString(_selector)).append("\n");
    if (_ignoreResult) {
        cfg.append(prefix).append("ignoreresult true\n");
    }
    uint32_t numRecipients = _recipients.size();
    if (numRecipients > 0) {
        cfg.append(prefix).append("recipient[").append(make_string("%d", numRecipients)).append("]\n");
        for (uint32_t i = 0; i < numRecipients; ++i) {
            cfg.append(prefix).append("recipient[").append(make_string("%d", i)).append("] ");
            cfg.append(RoutingSpec::toConfigString(_recipients[i])).append("\n");
        }
    }
}

string
HopSpec::toString() const
{
    string ret = "";
    toConfig(ret, "");
    return ret;
}

bool
HopSpec::operator==(const HopSpec &rhs) const
{
    if (_name != rhs._name) {
        return false;
    }
    if (_selector != rhs._selector) {
        return false;
    }
    if (_recipients.size() != rhs._recipients.size()) {
        return false;
    }
    for (uint32_t i = 0, len = _recipients.size(); i < len; ++i) {
        if (_recipients[i] != rhs._recipients[i]) {
            return false;
        }
    }
    return true;
}


} // namespace mbus
