// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/slobrok/sbmirror.h>
#include <vespa/config/common/configsystem.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/fnet/frt/supervisor.h>
#include <vespa/fnet/frt/target.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <sys/time.h>
#include <thread>
#include <cstdlib>

#include <vespa/log/log.h>
LOG_SETUP("vespa-proton-cmd");

namespace pandora::rtc_cmd {

class App
{
private:
    App(const App &);
    App& operator=(const App &);

    std::unique_ptr<fnet::frt::StandaloneFRT> _frt;
    FRT_Target     *_target;
    FRT_RPCRequest *_req;

public:
    App()
        : _frt(),
          _target(nullptr),
          _req(nullptr)
    {}
    ~App()
    {
        assert(!_frt);
        assert(_target == nullptr);
        assert(_req == nullptr);
    }

    int usage(const char *self)
    {
        fprintf(stderr, "usage: %s <port|spec|--local|--id=name> <cmd> [args]\n", self);
        fprintf(stderr, "die\n");
        fprintf(stderr, "getProtonStatus\n");
        fprintf(stderr, "getState\n");
        fprintf(stderr, "triggerFlush\n");
        fprintf(stderr, "prepareRestart\n");
        return 1;
    }

    void initRPC()
    {
        _frt = std::make_unique<fnet::frt::StandaloneFRT>();
        _req = _frt->supervisor().AllocRPCRequest();
    }

    void invokeRPC(bool print, double timeout=5.0)
    {
        if (_req == nullptr)
            return;

        _target->InvokeSync(_req, timeout);
        if (print || _req->IsError())
            _req->Print(0);
    }

    void finiRPC()
    {
        if (_req != nullptr) {
            _req->internal_subref();
            _req = nullptr;
        }
        if (_target != nullptr) {
            _target->internal_subref();
            _target = nullptr;
        }
        if (_frt) {
            _frt.reset();
        }
    }

    void
    scanSpecs(slobrok::api::MirrorAPI::SpecList &specs,
              const std::string &me,
              std::string &service,
              std::string &spec,
              int &found)
    {
        for (size_t j = 0; j < specs.size(); ++j) {
            if (specs[j].first == service)
                continue;
            if (specs[j].second.compare(0, me.length(), me) == 0) {
                service = specs[j].first;
                spec = specs[j].second;
                printf("found local RTC '%s' with connection spec %s\n",
                       specs[j].first.c_str(), spec.c_str());
                ++found;
            }
        }
    }

    std::string findRTC() {
        std::string me = "tcp/";
        me += vespalib::HostName::get().c_str();
        me += ":";

        std::string rtcPattern = "search/cluster.*/c*/r*/realtimecontroller";
        std::string rtcPattern2 = "*/search/cluster.*/*/realtimecontroller";
        std::string rtcPattern3 = "*/search/*/realtimecontroller";

        try {
            slobrok::ConfiguratorFactory sbcfg(config::ConfigUri("client"));
            slobrok::api::MirrorAPI sbmirror(_frt->supervisor(), sbcfg);
            for (int timeout = 1; timeout < 20; timeout++) {
                if (!sbmirror.ready()) {
                    std::this_thread::sleep_for(50ms*timeout);
                }
            }
            if (!sbmirror.ready()) {
                fprintf(stderr,
                        "ERROR: no data from service location broker\n");
                std::_Exit(1);
            }
            slobrok::api::MirrorAPI::SpecList specs = sbmirror.lookup(rtcPattern);
            slobrok::api::MirrorAPI::SpecList specs2 = sbmirror.lookup(rtcPattern2);
            slobrok::api::MirrorAPI::SpecList specs3 = sbmirror.lookup(rtcPattern3);

            int found = 0;
            std::string service;
            std::string spec;

            printf("looking for RTCs matching '%s' (length %d)\n",
                   me.c_str(), (int)me.length());
            scanSpecs(specs, me, service, spec, found);
            scanSpecs(specs2, me, service, spec, found);
            scanSpecs(specs3, me, service, spec, found);
            if (found > 1) {
                fprintf(stderr, "found more than one local RTC, you must use --id=<name>\n");
                std::_Exit(1);
            }
            if (found < 1) {
                fprintf(stderr, "found no local RTC, you must use --id=<name> (list follows):\n");
                for (size_t j = 0; j < specs.size(); ++j) {
                    printf("RTC name %s with connection spec %s\n",
                           specs[j].first.c_str(), specs[j].second.c_str());
                }
                std::_Exit(1);
            }
            return spec;
        } catch (config::InvalidConfigException& e) {
            fprintf(stderr, "ERROR: failed to get service location broker configuration\n");
            std::_Exit(1);
        } catch (vespalib::IllegalStateException& e) {
            fprintf(stderr, "ERROR: empty or invalid service location broker configuration: %s\n", e.what());
            std::_Exit(2);
        }
        return "";
    }

    std::string findRTC(std::string id) {
        std::string rtcPattern = "search/cluster.*/c*/r*/realtimecontroller";

        try {
            slobrok::ConfiguratorFactory sbcfg(config::ConfigUri("client"));
            slobrok::api::MirrorAPI sbmirror(_frt->supervisor(), sbcfg);
            for (int timeout = 1; timeout < 20; timeout++) {
                if (!sbmirror.ready()) {
                    std::this_thread::sleep_for(50ms*timeout);
                }
            }
            if (!sbmirror.ready()) {
                throw std::runtime_error("ERROR: no data from service location broker");
            }
            slobrok::api::MirrorAPI::SpecList specs = sbmirror.lookup(id);

            int found = 0;
            std::string spec;

            for (size_t j = 0; j < specs.size(); ++j) {
                std::string name = specs[j].first;
                spec = specs[j].second;
                printf("found RTC '%s' with connection spec %s\n",
                       name.c_str(), spec.c_str());
                ++found;
            }
            if (found > 1) {
                throw std::runtime_error("found more than one RTC, use a more specific id");
            }
            if (found < 1) {
                specs = sbmirror.lookup(rtcPattern);

                std::string msg = vespalib::make_string("found no RTC named '%s' (list follows):\n", id.c_str());
                for (size_t j = 0; j < specs.size(); ++j) {
                    msg += vespalib::make_string("RTC name %s with connection spec %s\n", specs[j].first.c_str(), specs[j].second.c_str());
                }
                throw std::runtime_error(msg);
            }
            return spec;
        } catch (config::InvalidConfigException& e) {
            throw std::runtime_error("ERROR: failed to get service location broker configuration");
        }
        return "";
    }


    int main(int argc, char **argv)
    {
        if (argc < 3) {
            return usage(argv[0]);
        }

        config::ConfigSystem configSystem;
        if (!configSystem.isUp()) {
            fprintf(stderr, "Config system is not up. Verify that vespa is started.");
            return 3;
        }
        try {
            initRPC();
        } catch (vespalib::Exception &e) {
            fprintf(stderr, "Exception in network initialization: %s", e.what());
            return 2;
        }
        int port = 0;
        std::string spec = argv[1];

        try {
            if (spec == "--local") {
                spec = findRTC();
            } else if (spec.compare(0, 5, "--id=") == 0) {
                spec = findRTC(spec.substr(5));
            } else {
                port = atoi(argv[1]);
            }
        } catch (const std::runtime_error & e) {
            fprintf(stderr, "%s", e.what());
            finiRPC();
            return 1;
        } catch (const config::ConfigTimeoutException & e) {
            fprintf(stderr, "Getting config timed out: %s", e.what());
            finiRPC();
            return 2;
        }

        if (port == 0 && spec.compare(0, 4, "tcp/") != 0) {
            finiRPC();
            return usage(argv[0]);
        }

        if (port != 0) {
            _target = _frt->supervisor().GetTarget(port);
        } else {
            _target = _frt->supervisor().GetTarget(spec.c_str());
        }

        bool invoked = false;

        if (strcmp(argv[2], "getState") == 0 &&
                   argc >= 3) {
            _req->SetMethodName("pandora.rtc.getState");

            FRT_Values &params = *_req->GetParams();

            params.AddInt32(argc > 3 ? atoi(argv[3]) : 0);
            params.AddInt32(argc > 4 ? atoi(argv[4]) : 0);
            invokeRPC(false);
            invoked = true;

            FRT_Values &rvals = *_req->GetReturn();

            if (!_req->IsError()) {
                FRT_Value &names = rvals.GetValue(0);
                FRT_Value &values = rvals.GetValue(1);
                FRT_Value &gencnt = rvals.GetValue(2);

                for (unsigned int i = 0;
                     i < names._string_array._len &&
                                      i < values._string_array._len;
                     i++)
                {
                    printf("\"%s\", \"%s\"\n",
                           names._string_array._pt[i]._str,
                           values._string_array._pt[i]._str);
                }
                printf("gencnt=%u\n",
                       static_cast<unsigned int>(gencnt._intval32));
            }
        } else if (strcmp(argv[2], "getProtonStatus") == 0 &&
                   argc >= 3) {

            _req->SetMethodName("proton.getStatus");
            FRT_Values &params = *_req->GetParams();
            params.AddString(argc > 3 ? argv[3] : "");
            invokeRPC(false);
            invoked = true;
            FRT_Values &rvals = *_req->GetReturn();
            if (!_req->IsError()) {
                FRT_Value &components = rvals.GetValue(0);
                FRT_Value &states = rvals.GetValue(1);
                FRT_Value &internalStates = rvals.GetValue(2);
                FRT_Value &messages = rvals.GetValue(3);
                for (unsigned int i = 0; i < components._string_array._len &&
                                         i < states._string_array._len &&
                                         i < internalStates.
                                               _string_array._len &&
                                         i < messages._string_array._len;
                                       i++) {
                    printf("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                           components._string_array._pt[i]._str,
                           states._string_array._pt[i]._str,
                           internalStates._string_array._pt[i]._str,
                           messages._string_array._pt[i]._str);
                }

            }
        } else if (strcmp(argv[2], "triggerFlush") == 0) {
            _req->SetMethodName("proton.triggerFlush");
            invokeRPC(false, 86400.0);
            invoked = true;
            if (! _req->IsError()) {
                printf("OK: flush trigger enabled\n");
            }
        } else if (strcmp(argv[2], "prepareRestart") == 0) {
            _req->SetMethodName("proton.prepareRestart");
            invokeRPC(false, 600.0);
            invoked = true;
            if (! _req->IsError()) {
                printf("OK: prepareRestart enabled\n");
            }
        } else if (strcmp(argv[2], "die") == 0) {
            _req->SetMethodName("pandora.rtc.die");
        } else {
            finiRPC();
            return usage(argv[0]);
        }
        if (!invoked)
            invokeRPC(true);
        finiRPC();
        return 0;
    }
};

} // namespace pandora::rtc_cmd


int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    pandora::rtc_cmd::App app;
    return app.main(argc, argv);
}
