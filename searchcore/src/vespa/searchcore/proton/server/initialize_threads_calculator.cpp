// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "initialize_threads_calculator.h"
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <fstream>

using vespalib::CpuUsage;
using vespalib::ThreadStackExecutor;
using CpuCategory = vespalib::CpuUsage::Category;

namespace {

void
write(const vespalib::string& path, uint32_t num_threads)
{
    std::ofstream file;
    file.open(path);
    file << num_threads;
    file.close();
}

uint32_t
read(const vespalib::string& path)
{
    std::ifstream file;
    file.open(path);
    uint32_t result;
    file >> result;
    file.close();
    return result;
}

VESPA_THREAD_STACK_TAG(proton_initialize_executor)

const vespalib::string file_name = "initialize-threads.txt";

}

namespace proton {

InitializeThreadsCalculator::InitializeThreadsCalculator(const HwInfo::Cpu & cpu_info,
                                                         const vespalib::string& base_dir,
                                                         uint32_t configured_num_threads)
    : _path(base_dir + "/" + file_name),
      _num_threads(std::min(cpu_info.cores(), configured_num_threads)),
      _threads()
{
    if (std::filesystem::exists(_path)) {
        _num_threads = read(_path.c_str());
        _num_threads = std::max(1u, (_num_threads / 2));
        std::filesystem::remove(_path);
    }
    write(_path.c_str(), _num_threads);
    if (_num_threads > 0) {
        _threads = std::make_shared<ThreadStackExecutor>(_num_threads, CpuUsage::wrap(proton_initialize_executor, CpuCategory::SETUP));
    }
}

InitializeThreadsCalculator::~InitializeThreadsCalculator() = default;

void
InitializeThreadsCalculator::init_done()
{
    std::filesystem::remove(_path);
    _threads = InitializeThreads();
}

}

