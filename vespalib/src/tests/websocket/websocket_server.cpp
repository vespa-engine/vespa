// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/websocket/websocket_server.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <thread>
#include <chrono>

using namespace vespalib;

vespalib::string read_file(const vespalib::string &file_name) {
    return MappedFileInput(file_name).get().make_string();
}

vespalib::string find_content_type(const vespalib::string &file_name) {
    if (ends_with(file_name, ".html")) {
        return "text/html";
    }
    if (ends_with(file_name, ".js")) {
        return "text/javascript";
    }
    if (ends_with(file_name, ".ico")) {
        return "image/x-icon";
    }    
    return "text/plain";
}

int main(int, char **) {
    ws::WebsocketServer::StaticRepo repo;
    for (vespalib::string file_name: { "index.html", "test.html", "favicon.ico" }) {
        vespalib::string content = read_file(file_name);
        vespalib::string content_type = find_content_type(file_name);
        if (!content.empty()) {
            fprintf(stderr, "loaded file: %s as content %s\n", file_name.c_str(), content_type.c_str());
            repo.emplace("/" + file_name, ws::WebsocketServer::StaticPage{content_type, content});
        }
    }
    ws::WebsocketServer server(0, std::move(repo));
    int port = server.port();
    SignalHandler::INT.hook();
    fprintf(stderr, "running websocket server at http://%s:%d/index.html\n",
            HostName::get().c_str(), port);
    fprintf(stderr, "use ^C (SIGINT) to exit\n");
    while (!SignalHandler::INT.check()) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }
    fprintf(stderr, "exiting...\n");    
    kill(getpid(), SIGTERM);
    return 0;
}
