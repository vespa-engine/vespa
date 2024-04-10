// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <absl/debugging/failure_signal_handler.h>

#include <vespa/log/log.h>
LOG_SETUP("storage_filestorage_gtest_runner");

int main(int argc, char* argv[]) {
    absl::FailureSignalHandlerOptions opts;
    opts.call_previous_handler = true;
    opts.use_alternate_stack = false; // Suboptimal, but needed to get proper backtracing (for some reason...)
    absl::InstallFailureSignalHandler(opts);

    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
