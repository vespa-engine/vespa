// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/util/threadexecutor.h>
#include <filesystem>
#include <memory>

namespace proton {

/**
 * Class that is used to calculate the number of threads to use
 * during the initialization of proton components.
 *
 * The number of threads is cut in half each time the initialization of proton components is aborted,
 * e.g. due to running out of memory.
 * This adjustment should ensure that we eventually are able to initialize and start proton.
 */
 class InitializeThreadsCalculator {
 private:
     using InitializeThreads = std::shared_ptr<vespalib::ThreadExecutor>;
     std::filesystem::path _path;
     uint32_t _num_threads;
     InitializeThreads _threads;

 public:
     InitializeThreadsCalculator(const HwInfo::Cpu & cpu_info, const vespalib::string& base_dir, uint32_t configured_num_threads);
     ~InitializeThreadsCalculator();
     uint32_t num_threads() const { return _num_threads; }
     InitializeThreads threads() const { return _threads; }
     void init_done();
 };

}
