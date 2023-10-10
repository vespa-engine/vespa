// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("");

int
main(int, char **)
{
    LOG(info, "LD_LIBRARY_PATH='%s'", getenv("LD_LIBRARY_PATH"));
    return 0;
}
