// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "hopblueprint.h"
#include "hopspec.h"

namespace mbus {

HopBlueprint::HopBlueprint(const HopSpec &spec) :
    _selector(),
    _recipients(),
    _ignoreResult(spec.getIgnoreResult())
{
    Hop hop = Hop::parse(spec.getSelector());
    for (uint32_t i = 0; i < hop.getNumDirectives(); ++i) {
        _selector.emplace_back(hop.getDirectiveSP(i));
    }
    std::vector<string> lst;
    for (uint32_t i = 0; i < spec.getNumRecipients(); ++i) {
        lst.emplace_back(spec.getRecipient(i));
    }
    for (const string & recipient : lst) {
        _recipients.emplace_back(Hop::parse(recipient));
    }
}

string
HopBlueprint::toString() const
{
    string ret = "HopBlueprint(selector = { ";
    for (uint32_t i = 0; i < _selector.size(); ++i) {
        ret.append("'");
        ret.append(_selector[i]->toString());
        ret.append("'");
        if (i < _selector.size() - 1) {
            ret.append(", ");
        }
    }
    ret.append(" }, recipients = { ");
    for (uint32_t i = 0; i < _recipients.size(); ++i) {
        ret.append("'");
        ret.append(_recipients[i].toString());
        ret.append("'");
        if (i < _recipients.size() - 1) {
            ret.append(", ");
        }
    }
    ret.append(" }, ignoreResult = ");
    ret.append(_ignoreResult ? "true" : "false");
    ret.append(")");
    return ret;
}

} // namespace mbus
