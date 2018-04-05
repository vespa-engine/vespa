// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fnet/fnet.h>
#include <vespa/slobrok/server/sbenv.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/fastos/app.h>
#include <csignal>

#include <vespa/log/log.h>
LOG_SETUP("vespa-slobrok");

/**
 * @brief namespace for the actual slobrok application.
 **/
namespace slobrok {

class App : public FastOS_Application
{
public:
    int Main() override;
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
App::Main()
{
    uint32_t portnum = 2773;
    vespalib::string cfgId;

    int argi = 1;
    const char* optArg;
    char c;
    while ((c = GetOpt("c:s:p:", optArg, argi)) != -1) {
        switch (c) {
        case 'c':
            cfgId = std::string(optArg);
            break;
        case 'p':
            portnum = atoi(optArg);
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
            mainobj.reset(new SBEnv(shim));
        } else {
            ConfigShim shim(portnum, cfgId);
            mainobj.reset(new SBEnv(shim));
        }
        hook_sigterm();
        res = mainobj->MainLoop();
    } catch (const config::ConfigTimeoutException &e) {
        LOG(error, "config timeout during construction : %s", e.what());
        EV_STOPPING("slobrok", "config timeout during construction");
        return 1;
    } catch (const vespalib::PortListenException &e) {
        LOG(error, "Failed listening to network port(%d) with protocol(%s): '%s'",
                   e.get_port(), e.get_protocol().c_str(), e.what());
        EV_STOPPING("slobrok", "could not listen to our network port");
        return 1;
    } catch (const std::exception & e) {
        LOG(error, "unknown exception during construction : %s", e.what());
        EV_STOPPING("slobrok", "unknown exception during construction");
        return 2;
    } catch (...) {
        LOG(error, "unknown exception during construction");
        EV_STOPPING("slobrok", "unknown exception during construction");
        return 3;
    }
    mainobj.reset();
    return res;
}

} // namespace slobrok

int
main(int argc, char **argv)
{
    slobrok::App slobrok;
    return slobrok.Entry(argc, argv);
}
