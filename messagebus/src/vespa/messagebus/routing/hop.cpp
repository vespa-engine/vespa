// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "hop.h"
#include "routeparser.h"

namespace mbus {

Hop::Hop() :
    _selector(),
    _ignoreResult(false)
{ }

Hop::Hop(const string &selector) :
    _selector(),
    _ignoreResult(false)
{
    Hop hop = parse(selector);
    _selector.swap(hop._selector);
    _ignoreResult = hop._ignoreResult;
}

Hop::Hop(std::vector<IHopDirective::SP> selector, bool ignoreResult) :
    _selector(std::move(selector)),
    _ignoreResult(ignoreResult)
{ }

Hop::Hop(const Hop &) = default;
Hop & Hop::operator = (const Hop &) = default;
Hop::~Hop() { }

Hop &
Hop::addDirective(IHopDirective::SP dir)
{
    _selector.push_back(dir);
    return *this;
}

Hop &
Hop::setDirective(uint32_t i, IHopDirective::SP dir)
{
    _selector[i] = dir;
    return *this;
}

IHopDirective::SP
Hop::removeDirective(uint32_t i)
{
    IHopDirective::SP ret = _selector[i];
    _selector.erase(_selector.begin() + i);
    return ret;
}

Hop &
Hop::clearDirectives()
{
    _selector.clear();
    return *this;
}

Hop &
Hop::setIgnoreResult(bool ignoreResult)
{
    _ignoreResult = ignoreResult;
    return *this;
}

Hop
Hop::parse(const string &hop)
{
    return RouteParser::createHop(hop);
}

bool
Hop::matches(const Hop &hop) const
{
    if (_selector.size() != hop.getNumDirectives()) {
        return false;
    }
    for (uint32_t i = 0; i < hop.getNumDirectives(); ++i) {
        if (!_selector[i]->matches(*hop.getDirective(i))) {
            return false;
        }
    }
    return true;
}

string
Hop::toDebugString() const
{
    string ret = "Hop(selector = { ";
    for (uint32_t i = 0; i < _selector.size(); ++i) {
        ret.append(_selector[i]->toDebugString());
        if (i < _selector.size() - 1) {
            ret.append(", ");
        }
    }
    ret.append(" }, ignoreResult = ");
    ret.append(_ignoreResult ? "true" : "false");
    ret.append(")");
    return ret;
}

string
Hop::toString() const
{
    string ret = _ignoreResult ? "?" : "";
    ret.append(toString(0, _selector.size()));
    return ret;
}

string
Hop::toString(uint32_t fromIncluding, uint32_t toNotIncluding) const
{
    string ret = "";
    for (uint32_t i = fromIncluding; i < toNotIncluding; ++i) {
        ret.append(_selector[i]->toString());
        if (i < toNotIncluding - 1) {
            ret.append("/");
        }
    }
    return ret;
}

string
Hop::getPrefix(uint32_t toNotIncluding) const
{
    if (toNotIncluding > 0) {
        return toString(0, toNotIncluding) + "/";
    }
    return "";
}

string
Hop::getSuffix(uint32_t fromNotIncluding) const
{
    if (fromNotIncluding < _selector.size() - 1) {
        string ret = "/";
        ret.append(toString(fromNotIncluding + 1, _selector.size()));
        return ret;
    }
    return "";
}

} // namespace mbus
