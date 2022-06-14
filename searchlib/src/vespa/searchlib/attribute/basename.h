// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace search::attribute {

class BaseName : public vespalib::string
{
public:
    using string = vespalib::string;
    BaseName(vespalib::stringref s);
    BaseName(vespalib::stringref base, vespalib::stringref name);
    BaseName & operator = (vespalib::stringref s);
    ~BaseName();

    const string & getAttributeName() const { return _name; }
    string getDirName() const;
private:
    static string createAttributeName(vespalib::stringref s);
    string _name;
};

}
