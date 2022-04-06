// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/slobrok/server/sbenv.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <csignal>
#include <unistd.h>

#include <vespa/log/log.h>
LOG_SETUP("vespa-slobrok");

/**
 * @brief namespace for the actual slobrok application.
 **/
namespace slobrok {

class App
{
public:
    int main(int argc, char **argv);
};

static std::unique_ptr<SBEnv> mainobj;

extern "C" {
static void sigtermhandler(int signo);
};

static void
sigtermhandler(int signo)
{
    (void) signo;
    if (mainobj) {
        mainobj->shutdown();
    }
}

static void
hook_sigterm(void)
{
    struct sigaction act;
    act.sa_handler = sigtermhandler;
    sigemptyset(&act.sa_mask);
    act.sa_flags = 0;
    sigaction(SIGTERM, &act, NULL);
}


int
App::main(int argc, char **argv)
{
    uint32_t portnum = 2773;
    vespalib::string cfgId;

    int c;
    while ((c = getopt(argc, argv, "c:s:p:N")) != -1) {
        switch (c) {
        case 'c':
            cfgId = std::string(optarg);
            break;
        case 'p':
            portnum = atoi(optarg);
            break;
        case 'N':
            // ignored
            break;
        default:
            LOG(error, "unknown option letter '%c'", c);
            return 1;
        }
    }
    int res = 1;
    try {
        if (cfgId.empty()) {
            LOG(debug, "no config id specified");
            ConfigShim shim(portnum);
            mainobj = std::make_unique<SBEnv>(shim);
        } else {
            ConfigShim shim(portnum, cfgId);
            shim.enableStateServer(true);
            mainobj = std::make_unique<SBEnv>(shim);
        }
        hook_sigterm();
        res = mainobj->MainLoop();
    } catch (const config::ConfigTimeoutException &e) {
        LOG(error, "config timeout during construction : %s", e.what());
        EV_STOPPING("slobrok", "config timeout during construction");
        res = 1;
    } catch (const vespalib::PortListenException &e) {
        LOG(error, "Failed listening to network port(%d) with protocol(%s): '%s'",
                   e.get_port(), e.get_protocol().c_str(), e.what());
        EV_STOPPING("slobrok", "could not listen to our network port");
        res = 1;
    } catch (const std::exception & e) {
        LOG(error, "unknown exception during construction : %s", e.what());
        EV_STOPPING("slobrok", "unknown exception during construction");
        res = 2;
    }
    if (mainobj && !mainobj->isShuttingDown()) {
        mainobj->shutdown();
    }
    mainobj.reset();
    return res;
}

} // namespace slobrok

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    slobrok::App slobrok;
    return slobrok.main(argc, argv);
}
