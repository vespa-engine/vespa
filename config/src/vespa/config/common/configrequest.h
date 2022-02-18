// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>

namespace config {

class ConfigKey;
struct ConfigState;

/**
 * Baseclass for config requests.
 *
 * @author <a href="gv@yahoo-inc.com">Gj&oslash;ran Voldengen</a>
 * @version : $Id: configrequest.h 125304 2011-08-25 07:53:59Z Ulf Lilleengen $
 */

class ConfigRequest {
public:
    ConfigRequest() = default;
    ConfigRequest(const ConfigRequest&) = delete;
    ConfigRequest& operator=(const ConfigRequest&) = delete;
    virtual ~ConfigRequest() = default;
    virtual const ConfigKey & getKey() const = 0;
    /** Abort a request. */
    virtual bool abort() = 0;
    virtual void setError(int errorCode) = 0;
    virtual bool verifyState(const ConfigState & state) const = 0;

};

}

