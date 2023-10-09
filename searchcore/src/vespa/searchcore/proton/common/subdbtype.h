// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

/**
 * Enumeration of the different kinds of sub databases within a
 * document db.
 */
enum class SubDbType {
    READY = 0,
    REMOVED = 1,
    NOTREADY = 2,
    COUNT = 3/* number of valid subdb types, this value by itself is invalid */
};

}
