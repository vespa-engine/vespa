// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <string>

namespace search::attribute {

class BaseName : public std::string
{
public:
    using string = std::string;
    BaseName(std::string_view s);
    BaseName(std::string_view base, std::string_view name);
    BaseName & operator = (std::string_view s);
    ~BaseName();

    const string & getAttributeName() const { return _name; }
    string getDirName() const;
private:
    static string createAttributeName(std::string_view s);
    string _name;
};

}
