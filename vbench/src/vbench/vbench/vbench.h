// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "analyzer.h"
#include "generator.h"
#include "latency_analyzer.h"
#include "native_factory.h"
#include "qps_analyzer.h"
#include "qps_tagger.h"
#include "request_generator.h"
#include "request_scheduler.h"
#include "request_sink.h"
#include "server_tagger.h"
#include "tagger.h"
#include <vbench/core/taintable.h>
#include <vespa/vespalib/data/slime/slime.h>

namespace vbench {

class VBench : public vespalib::Runnable,
               public Taintable
{
private:
    struct InputChain {
        using UP = std::unique_ptr<InputChain>;
        std::vector<Tagger::UP>           taggers;
        Generator::UP                     generator;
        std::thread                       thread;
    };
    NativeFactory                _factory;
    std::vector<Analyzer::UP>    _analyzers;
    RequestScheduler::UP         _scheduler;
    std::vector<InputChain::UP>  _inputs;
    Taint                        _taint;

public:
    VBench(const vespalib::Slime &cfg);
    ~VBench();
    void abort();
    void run() override;
    const Taint &tainted() const override { return _taint; }
};

} // namespace vbench
