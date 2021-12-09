// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/programoptions.h>
#include <vespa/vespalib/util/thread.h>
#include <vespa/vespalib/util/runnable_pair.h>
#include <vbench/vbench/vbench.h>
#include <iostream>

using namespace vbench;

VESPA_THREAD_STACK_TAG(vbench_thread);

typedef vespalib::SignalHandler SIG;

struct NotifyDone : public vespalib::Runnable {
    vespalib::Gate &done;
    NotifyDone(vespalib::Gate &d) : done(d) {}
    void run() override {
        done.countDown();
    }
};

void setupSignals() {
    SIG::PIPE.ignore();
    SIG::INT.hook();
    SIG::TERM.hook();
}

int run(const std::string &cfg_name) {
    vespalib::MappedFileInput cfg_file(cfg_name);
    if (!cfg_file.valid()) {
        fprintf(stderr, "could not load config file: %s\n", cfg_name.c_str());
        return 1;
    }
    vespalib::Slime cfg;
    vespalib::Memory mapped_cfg(cfg_file.get().data, cfg_file.get().size);
    if (!vespalib::slime::JsonFormat::decode(mapped_cfg, cfg)) {
        fprintf(stderr, "unable to parse config file: %s\n",
                cfg.toString().c_str());
        return 1;
    }
    setupSignals();
    vespalib::Gate done;
    VBench vbench(cfg);
    NotifyDone notify(done);
    vespalib::RunnablePair runBoth(vbench, notify);
    vespalib::Thread thread(runBoth, vbench_thread);
    thread.start();
    while (!SIG::INT.check() && !SIG::TERM.check() && !done.await(1s)) {}
    if (!done.await(vespalib::duration::zero())) {
        vbench.abort();
        done.await();
    }
    if (vbench.tainted()) {
        fprintf(stderr, "vbench failed: %s\n",
                vbench.tainted().reason().c_str());
        return 1;
    }
    return 0;
}

int usage(const char *prog) {
    fprintf(stderr, "vbench -- vespa benchmarking tool\n\n");
    fprintf(stderr, "usage: %s run <config-file>\n", prog);
    fprintf(stderr, "  run benchmarking as described in the config file.\n\n");
    return 1;
}

int main(int argc, char **argv) {
    if (argc > 1) {
        std::string mode = argv[1];
        if (mode == "run" && argc == 3) {
            return run(argv[2]);
        }
    }
    return usage(argv[0]);
}
