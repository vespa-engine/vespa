// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basename.h"

namespace search::attribute {

BaseName::BaseName(vespalib::stringref base, vespalib::stringref name)
    : string(base),
      _name(name)
{
    if (!empty()) {
        push_back('/');
    }
    append(name);
}

BaseName::BaseName(vespalib::stringref s)
    : string(s),
      _name(createAttributeName(s))
{ }
BaseName &
BaseName::operator = (vespalib::stringref s) {
        BaseName n(s);
        std::swap(*this, n);
        return *this;
    }

BaseName::~BaseName() = default;

vespalib::string
BaseName::createAttributeName(vespalib::stringref s)
{
    size_t p(s.rfind('/'));
    if (p == string::npos) {
       return s;
    } else {
        return s.substr(p+1);
    }
}

vespalib::string
BaseName::getDirName() const
{
    size_t p = rfind('/');
    if (p == string::npos) {
       return "";
    } else {
        return substr(0, p);
    }
}

}
