// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/errorcode.h>
#include <vespa/vespalib/util/signalhandler.h>

using namespace mbus;

class App
{
public:
    void dump(const std::string &desc, uint32_t value);
    int main(int argc, char **argv);
};

void
App::dump(const std::string &desc, uint32_t value)
{
    fprintf(stdout, "%s => %u => \"%s\"\n", desc.c_str(), value,
            ErrorCode::getName(value).c_str());
}

int
App::main(int, char **)
{
    dump("NONE", ErrorCode::NONE);

    dump("SEND_QUEUE_FULL", ErrorCode::SEND_QUEUE_FULL);
    dump("NO_ADDRESS_FOR_SERVICE", ErrorCode::NO_ADDRESS_FOR_SERVICE);
    dump("CONNECTION_ERROR", ErrorCode::CONNECTION_ERROR);
    dump("UNKNOWN_SESSION", ErrorCode::UNKNOWN_SESSION);
    dump("SESSION_BUSY", ErrorCode::SESSION_BUSY);
    dump("SEND_ABORTED", ErrorCode::SEND_ABORTED);
    dump("HANDSHAKE_FAILED", ErrorCode::HANDSHAKE_FAILED);
    dump("first unused TRANSIENT_ERROR", ErrorCode::TRANSIENT_ERROR + 8);

    dump("SEND_QUEUE_CLOSED", ErrorCode::SEND_QUEUE_CLOSED);
    dump("ILLEGAL_ROUTE", ErrorCode::ILLEGAL_ROUTE);
    dump("NO_SERVICES_FOR_ROUTE", ErrorCode::NO_SERVICES_FOR_ROUTE);
    dump("ENCODE_ERROR", ErrorCode::ENCODE_ERROR);
    dump("NETWORK_ERROR", ErrorCode::NETWORK_ERROR);
    dump("UNKNOWN_PROTOCOL", ErrorCode::UNKNOWN_PROTOCOL);
    dump("DECODE_ERROR", ErrorCode::DECODE_ERROR);
    dump("TIMEOUT", ErrorCode::TIMEOUT);
    dump("INCOMPATIBLE_VERSION", ErrorCode::INCOMPATIBLE_VERSION);
    dump("UNKNOWN_POLICY", ErrorCode::UNKNOWN_POLICY);
    dump("NETWORK_SHUTDOWN", ErrorCode::NETWORK_SHUTDOWN);
    dump("POLICY_ERROR", ErrorCode::POLICY_ERROR);
    dump("SEQUENCE_ERROR", ErrorCode::SEQUENCE_ERROR);
    dump("first unused FATAL_ERROR", ErrorCode::FATAL_ERROR + 15);

    dump("max UNKNOWN below", ErrorCode::TRANSIENT_ERROR - 1);
    dump("min TRANSIENT_ERROR", ErrorCode::TRANSIENT_ERROR);
    dump("max TRANSIENT_ERROR", ErrorCode::TRANSIENT_ERROR + 49999);
    dump("min APP_TRANSIENT_ERROR", ErrorCode::APP_TRANSIENT_ERROR);
    dump("max APP_TRANSIENT_ERROR", ErrorCode::APP_TRANSIENT_ERROR + 49999);
    dump("min FATAL_ERROR", ErrorCode::FATAL_ERROR);
    dump("max FATAL_ERROR", ErrorCode::FATAL_ERROR + 49999);
    dump("min APP_FATAL_ERROR", ErrorCode::APP_FATAL_ERROR);
    dump("max APP_FATAL_ERROR", ErrorCode::APP_FATAL_ERROR + 49999);
    dump("min UNKNOWN above", ErrorCode::ERROR_LIMIT);
    return 0;
}

int main(int argc, char **argv) {
    vespalib::SignalHandler::PIPE.ignore();
    App app;
    return app.main(argc, argv);
}
