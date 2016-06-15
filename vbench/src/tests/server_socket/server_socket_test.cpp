// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/util/thread.h>

using namespace vbench;

TEST_MT_F("require that close will interrupt accept", 2, ServerSocket()) {
    if (thread_id == 0) {
        for (;;) {
            Stream::UP stream = f1.accept();
            if (stream.get() == 0) {
                break;
            }
        }
        Stream::UP s2 = f1.accept();
        EXPECT_TRUE(s2.get() == 0);
    } else {
        vespalib::Thread::sleep(20);
        f1.close();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
