// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <string>

namespace config {

class ConfigValue;
class ConfigKey;
struct ConfigState;
class Trace;

/**
 * Baseclass for config responses.
 */
class ConfigResponse {
public:
    virtual ~ConfigResponse() = default;

    virtual const ConfigKey & getKey() const = 0;
    virtual const ConfigValue & getValue() const = 0;

    virtual const ConfigState & getConfigState() const = 0;
    virtual const Trace & getTrace() const = 0;

    virtual bool hasValidResponse() const = 0;

    /**
     * Verifies that the returned response meets any criteria (decided by the implementation) to use the getters
     * for return values. The result from this validation can be found without performing the validation
     * again by calling {@link ConfigResponse#hasValidResponse()}.
     *
     * @return true if the returned response meets criteria to use the getters for return values.
     */
    virtual bool validateResponse() = 0;

    /**
     * Fills all data received in the response in order to be able to retrieve
     * the config values. Should not be called before the response has been
     * validated.
     */
    virtual void fill() = 0;

    /** @return Error message if a request has failed, null otherwise. */
    virtual std::string errorMessage() const = 0;

    virtual int errorCode() const = 0;

    virtual bool isError() const = 0;
};

}
