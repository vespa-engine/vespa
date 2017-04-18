// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vbench/http/http_result_handler.h>
#include <vbench/http/server_spec.h>
#include <vbench/http/http_client.h>

using namespace vbench;

class App : public FastOS_Application
{
public:
    int Main() override;
};

struct MyHttpHandler : public HttpResultHandler {
    virtual void handleHeader(const string &name, const string &value) override {
        fprintf(stderr, "got header: '%s': '%s'\n", name.c_str(), value.c_str());
    }
    virtual void handleContent(const Memory &data) override {
        fprintf(stderr, "got data: %zu bytes\n", data.size);
        fwrite(data.data, 1, data.size, stdout);
    }
    virtual void handleFailure(const string &reason) override {
        fprintf(stderr, "got FAILURE: '%s'\n", reason.c_str());
    }
};

int
App::Main()
{
    if (_argc != 4) {
        printf("usage: dumpurl <host> <port> <url>\n");
        return -1;
    }
    MyHttpHandler myHandler;
    bool ok = HttpClient::fetch(ServerSpec(_argv[1], atoi(_argv[2])), _argv[3], myHandler);
    return ok ? 0 : 1;
}

int main(int argc, char **argv) {
    App app;
    return app.Entry(argc, argv);
}
