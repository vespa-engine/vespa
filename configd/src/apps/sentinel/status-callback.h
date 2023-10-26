// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace config::sentinel {

/** very simple callback API with "ok" or "not ok" status only */
struct StatusCallback {
    virtual void returnStatus(bool ok) = 0;
protected:
    ~StatusCallback() = default;
};

}
