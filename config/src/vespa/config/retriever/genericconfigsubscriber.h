// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/config/subscription/configsubscriptionset.h>

namespace config {

/**
 * The GenericConfigSubscriber is a generic form of a config subscriber, which
 * does not require any type to be known. It also only supports generation
 * changes.
 */
class GenericConfigSubscriber
{
public:
    GenericConfigSubscriber(std::shared_ptr<IConfigContext> context);
    bool nextGeneration(vespalib::duration timeout);
    std::shared_ptr<ConfigSubscription> subscribe(const ConfigKey & key, vespalib::duration timeout);
    void close();
    int64_t getGeneration() const;
private:
    ConfigSubscriptionSet _set;
};

} // namespace config

