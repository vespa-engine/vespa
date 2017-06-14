// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

    NamedService(const char *name, const char *spec);
    virtual ~NamedService();

    const char *getName() const { return _name.c_str(); }
    const char *getSpec() const { return _spec.c_str(); }
};

//-----------------------------------------------------------------------------

} // namespace slobrok

