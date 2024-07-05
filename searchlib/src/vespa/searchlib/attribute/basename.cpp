// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "basename.h"

namespace search::attribute {

BaseName::BaseName(std::string_view base, std::string_view name)
    : string(base),
      _name(name)
{
    if (!empty()) {
        push_back('/');
    }
    append(name);
}

BaseName::BaseName(std::string_view s)
    : string(s),
      _name(createAttributeName(s))
{ }
BaseName &
BaseName::operator = (std::string_view s) {
        BaseName n(s);
        std::swap(*this, n);
        return *this;
    }

BaseName::~BaseName() = default;

vespalib::string
BaseName::createAttributeName(std::string_view s)
{
    size_t p(s.rfind('/'));
    if (p == string::npos) {
       return vespalib::string(s);
    } else {
        return vespalib::string(s.substr(p+1));
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
