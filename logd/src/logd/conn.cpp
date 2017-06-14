// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <fcntl.h>
#include <errno.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#include <vespa/log/log.h>
LOG_SETUP("");
LOG_RCSID("$Id$");

#include "errhandle.h"
#include <logd/config-logd.h>
#include "conn.h"
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/net/socket_address.h>

namespace logdemon {

static int retryBeforeWarningCount = 20;

int makeconn(const char *logSrvHost, int logPort)
{
    auto handle = vespalib::SocketAddress::select_remote(logPort, logSrvHost).connect();
    if (!handle) {
        const char *msgfmt = "Cannot connect to logserver on %s:%d: %s";
        if (retryBeforeWarningCount > 0) {
            --retryBeforeWarningCount;
            LOG(debug, msgfmt, logSrvHost, logPort, strerror(errno));
        } else {
            LOG(warning, msgfmt, logSrvHost, logPort, strerror(errno));
        }
        return -1;
    }
    LOG(debug,
        "Made new connection to port %d. Connected to daemon.",
        logPort);
    return handle.release();
}

} // namespace
