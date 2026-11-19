// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "initialize_threads_calculator.h"

#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

#include <fstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.initialize_threads_calculator");

using vespalib::CpuUsage;
using vespalib::ThreadStackExecutor;
using CpuCategory = vespalib::CpuUsage::Category;

namespace {

bool write(const std::string& path, uint32_t num_threads) {
    std::ofstream file;
    file.open(path);
    if (!(file << num_threads)) {
        return false;
    }
    file.close();
    return !file.fail();
}

void atomic_write(const std::filesystem::path& path, uint32_t num_threads) {
    // Write and fsync to temp path
    auto temp_path = std::filesystem::path(path).concat(".tmp");
    bool success = false;
    if (write(temp_path.c_str(), num_threads)) {
        vespalib::File::sync(temp_path.c_str());

        // Rename to actual path and fsync parent dir
        std::error_code ec;
        std::filesystem::rename(temp_path, path, ec);
        if (!ec) {
            auto parent_dir = path.parent_path();
            vespalib::File::sync(parent_dir.c_str());
            success = true;
        }
    }
    if (!success) {
        LOG(warning, "Failed to write headstone file. Thread count will not be reduced should initialization fail.");
    }
}

uint32_t read(const std::string& path, uint32_t fallback_value) {
    std::ifstream file;
    file.open(path);
    uint32_t result;
    if (file >> result) {
        return result;
    } else {
        return fallback_value;
    }
}

VESPA_THREAD_STACK_TAG(proton_initialize_executor)

const std::string file_name = "initialize-threads.txt";

} // namespace

namespace proton {

InitializeThreadsCalculator::InitializeThreadsCalculator(const vespalib::HwInfo::Cpu& cpu_info,
                                                         const std::string& base_dir, uint32_t configured_num_threads)
    : _path(base_dir + "/" + file_name),
      _num_threads(std::min(cpu_info.cores(), configured_num_threads)),
      _threads() {
    // You are now playing "Vespa: The Rogue-Like".
    // A new run begins.
    // What is this? Our own headstone?
    if (std::filesystem::exists(_path)) {
        // The engraving on the headstone is hard to decipher.
        uint32_t num_threads_from_file = read(_path.c_str(), _num_threads); // _num_threads as fallback
        _num_threads = std::min(num_threads_from_file, _num_threads);       // Clamp in case of corrupted file

        // We did our best to make out the cause of death:
        // Using _num_threads threads was foolish and brought doom upon us.
        // Let us try our luck with half that number in this run.
        _num_threads = std::max(1u, (_num_threads / 2));
    }
    // We preemptively place our headstone.
    // May it serve as a warning should we not make it through this run.
    atomic_write(_path, _num_threads); // atomically overwrite previous file
    if (_num_threads > 0) {
        _threads = std::make_shared<ThreadStackExecutor>(
            _num_threads, CpuUsage::wrap(proton_initialize_executor, CpuCategory::SETUP));
    }
}

InitializeThreadsCalculator::~InitializeThreadsCalculator() = default;

void InitializeThreadsCalculator::init_done() {
    // We are still alive. We made it.
    // Tear down the headstone to celebrate our victory.
    std::error_code ec;
    std::filesystem::remove(_path, ec);
    if (ec) {
        LOG(warning, "Failed to remove headstone file. Thread count will be reduced in next initialization.");
    }
    _threads = InitializeThreads();
}

} // namespace proton
