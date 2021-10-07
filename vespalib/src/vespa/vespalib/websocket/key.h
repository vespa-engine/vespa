// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace ws {

struct Key
{
    /**
     * Create a new random key that can be used by a client to request
     * a version 13 websocket connection upgrade.
     **/
    static vespalib::string create();

    /**
     * Generate a version 13 websocket handshake accept token for a
     * client key.
     **/
    static vespalib::string accept(const vespalib::string &key);
};

} // namespace vespalib::ws
} // namespace vespalib
