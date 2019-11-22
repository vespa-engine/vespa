// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    using milliseconds = std::chrono::milliseconds;
    GenericConfigSubscriber(const IConfigContext::SP & context);
    bool nextGeneration(milliseconds timeoutInMillis);
    ConfigSubscription::SP subscribe(const ConfigKey & key, milliseconds timeoutInMillis);
    void close();
    int64_t getGeneration() const;
private:
    ConfigSubscriptionSet _set;
};

} // namespace config

