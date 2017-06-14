// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
        _selector.push_back(hop.getDirective(i));
    }
    std::vector<string> lst;
    for (uint32_t i = 0; i < spec.getNumRecipients(); ++i) {
        lst.push_back(spec.getRecipient(i));
    }
    for (std::vector<string>::iterator it = lst.begin();
         it != lst.end(); ++it)
    {
        _recipients.push_back(Hop::parse(*it));
    }
}

HopBlueprint &
HopBlueprint::setIgnoreResult(bool ignoreResult)
{
    _ignoreResult = ignoreResult;
    return *this;
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
