// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>

namespace proton {

class ProtonConfigSnapshot;

/*
 * Interface class to handle config changes to proton using config
 * snapshots spanning all document types.
 */
class IProtonConfigurer
{
public:
    virtual ~IProtonConfigurer() { }
    virtual void reconfigure(std::shared_ptr<ProtonConfigSnapshot> configSnapshot) = 0;
};

} // namespace proton
