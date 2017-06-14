// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/fdispatch/program/fdispatch.h>
#include <vespa/searchcore/fdispatch/common/perftask.h>
#include <vespa/vespalib/net/state_server.h>
#include <vespa/vespalib/net/simple_health_producer.h>
#include <vespa/vespalib/net/simple_metrics_producer.h>
#include <vespa/searchlib/expression/forcelink.hpp>
#include <vespa/searchlib/aggregation/forcelink.hpp>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/fastos/app.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("fdispatch");


using fdispatch::Fdispatch;
using vespa::config::search::core::FdispatchrcConfig;
using namespace std::literals;

extern char FastS_VersionTag[];

class FastS_FDispatchApp : public FastOS_Application
{
private:
    FastS_FDispatchApp(const FastS_FDispatchApp &);
    FastS_FDispatchApp& operator=(const FastS_FDispatchApp &);

protected:
    vespalib::string _configId;

    bool CheckShutdownFlags () const {
        return (vespalib::SignalHandler::INT.check() || vespalib::SignalHandler::TERM.check());
    }

    void Usage();
    bool GetOptions(int *exitCode);

public:
    int Main() override;
    FastS_FDispatchApp();
    ~FastS_FDispatchApp();
};


FastS_FDispatchApp::FastS_FDispatchApp()
{
}


/**
 * Main program for a dispatcher node.
 */
int
FastS_FDispatchApp::Main()
{
    int exitCode;

    forcelink_searchlib_expression();
    forcelink_searchlib_aggregation();

    if (!GetOptions(&exitCode)) {
        EV_STOPPING("fdispatch", (exitCode == 0) ? "clean shutdown" : "error");
        return exitCode;
    }

    exitCode = 0;

    EV_STARTED("fdispatch");

    vespalib::SignalHandler::INT.hook();
    vespalib::SignalHandler::TERM.hook();
    vespalib::SignalHandler::PIPE.ignore();

    std::unique_ptr<Fdispatch> myfdispatch;
    try {
        myfdispatch.reset(new Fdispatch(_configId));
    } catch (std::exception &ex) {
        LOG(error, "getting config: %s", (const char *) ex.what());
        exitCode = 1;
        EV_STOPPING("fdispatch", "error getting config");
        return exitCode;
    }

    try {

        if (!myfdispatch->Init()) {
            throw std::runtime_error("myfdispatch->Init(" + _configId + ") failed");
        }
        if (myfdispatch->Failed()) {
            throw std::runtime_error("myfdispatch->Failed()");
        }
        { // main loop scope
            vespalib::SimpleHealthProducer health;
            vespalib::SimpleMetricsProducer metrics;
            vespalib::StateServer stateServer(myfdispatch->getHealthPort(), health, metrics, myfdispatch->getComponentConfig());
            FastS_PerfTask perfTask(*myfdispatch, 300.0);
            while (!CheckShutdownFlags()) {
                if (myfdispatch->Failed()) {
                    throw std::runtime_error("myfdispatch->Failed()");
                }
                std::this_thread::sleep_for(100ms);
                if (!myfdispatch->CheckTempFail())
                    break;
            }
        } // main loop scope
        if (myfdispatch->Failed()) {
            throw std::runtime_error("myfdispatch->Failed()");
        }
    } catch (std::runtime_error &e) {
        LOG(warning, "got std::runtime_error during init: %s", e.what());
        exitCode = 1;
    } catch (std::exception &e) {
        LOG(error, "got exception during init: %s", e.what());
        exitCode = 1;
    } catch (...) {
        LOG(error, "got exception during init");
        exitCode = 1;
    }

    LOG(debug, "Deleting fdispatch");
    myfdispatch.reset();
    LOG(debug, "COMPLETION: Exiting");
    EV_STOPPING("fdispatch", (exitCode == 0) ? "clean shutdown" : "error");
    return exitCode;
}


bool
FastS_FDispatchApp::GetOptions(int *exitCode)
{
    int errflg = 0;
    int c;
    const char *optArgument;
    int longopt_index;	/* Shows which long option was used */
    static struct option longopts[] = {
        { "config-id", 1, NULL, 0 },
        { NULL, 0, NULL, 0 }
    };
    enum longopts_enum {
        LONGOPT_CONFIGID
    };
    int optIndex = 1;    // Start with argument 1
    while ((c = GetOptLong("c:", optArgument, optIndex, longopts, &longopt_index)) != -1) {
        switch (c) {
        case 0:
            switch (longopt_index) {
            case LONGOPT_CONFIGID:
                break;
            default:
                if (optArgument != NULL) {
                    LOG(info, "longopt %s with arg %s", longopts[longopt_index].name, optArgument);
                } else {
                    LOG(info, "longopt %s", longopts[longopt_index].name);
                }
                break;
            }
            break;
        case 'c':
            _configId = optArgument;
            break;
        case '?':
        default:
            errflg++;
        }
    }
    if (errflg) {
        Usage();
        *exitCode = 1;
        return false;
    }
    return true;
}

void
FastS_FDispatchApp::Usage()
{
    printf("FAST Search - fdispatch %s\n", FastS_VersionTag);
    printf("\n"
           "USAGE:\n"
           "\n"
           "fdispatch [-C fsroot] [-c rcFile] [-P preHttPort] [-V] [-w FFF]\n"
           "\n"
           "  -C fsroot      Fast Search's root directory\n"
           "                 (default /usr/fastsearch/fastserver4)\n"
           "  -c rcFile      fdispatchrc file (default FASTSEARCHROOT/etc/fdispatchrc)\n"
           "  -P preHttPort  pre-allocated socket number for http service\n"
           "  -V             show version and exit\n"
           "  -w FFF         hex value (max 32 bit) for the Verbose mask\n"
           "\n");
}


FastS_FDispatchApp::~FastS_FDispatchApp()
{
}


int
main(int argc, char **argv)
{
    FastS_FDispatchApp app;
    int retval;

    // Maybe this should be handeled by FastOS
    setlocale(LC_ALL, "C");

    retval = app.Entry(argc, argv);

    return retval;
}
