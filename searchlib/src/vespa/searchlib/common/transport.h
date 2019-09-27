// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::fs4transport {

/**
 * Instead of using a 32-bit number to send the 'usehardware' flag, we
 * now use this 32-bit number to send 32 flags. The currently defined flags
 * are as follows:
 * <ul>
 *  <li><b>QFLAG_EXTENDED_COVERAGE</b>: Indicates that the it is able to receive extended coverage information.</li>
 *  <li><b>QFLAG_COVERAGE_NODES</b>: Indicate that it is able to handle nodes information.</li>
 *  <li><b>QFLAG_ESTIMATE</b>: Indicates that the  query is performed to get
 *                             an estimate of the total number of hits</li>
 *  <li><b>QFLAG_DUMP_FEATURES</b>: Dump detailed ranking information. Note that
 *                             this flag will only be considered when sent in a
 *                             GETDOCSUMSX packet. Is is put here to avoid having
 *                             2 separate query related flag spaces</li>
 *  <li><b>QFLAG_DROP_SORTDATA</b>: Don't return any sort data even if sortspec
 *                             is used.</li>
 *  <li><b>QFLAG_NO_RESULTCACHE</b>: Do not use any result cache. Perform query no matter what.</li>
 * </ul>
 **/
enum queryflags {
    QFLAG_DROP_SORTDATA        = 0x00004000,
    QFLAG_DUMP_FEATURES        = 0x00040000
};

// docsum class for slime tunneling
const uint32_t SLIME_MAGIC_ID = 0x55555555;

}
