// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace config {

struct ReloadHandler
{
    /**
     * Reload any configs with a given generation.
     */
    virtual void reload(int64_t generation) = 0;
    virtual ~ReloadHandler() = default;
};

}

