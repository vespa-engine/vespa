// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <string>

namespace slobrok {

//-----------------------------------------------------------------------------

/**
 * @class NamedService
 * @brief Represents a server with a name and a connection specification.
 *
 * a NamedService is always part of a collection implementing the
 * IRpcSrvCollection interface.
 **/

class NamedService
{
protected:
    std::string           _name;
    std::string           _spec;

public:
    NamedService(const NamedService &) = delete;
    NamedService &operator=(const NamedService &) = delete;

    NamedService(const std::string & name, const std::string & spec);
    virtual ~NamedService();

    const std::string & getName() const { return _name; }
    const std::string & getSpec() const { return _spec; }
};

//-----------------------------------------------------------------------------

} // namespace slobrok

