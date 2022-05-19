// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "thread.h"

namespace storage::framework {

void
Thread::interruptAndJoin()
{
    interrupt();
    join();
}

void
Thread::interruptAndJoin(std::condition_variable &cv)
{
    interrupt();
    cv.notify_all();
    join();
}

}
