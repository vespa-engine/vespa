// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

using OutputWriter = vespalib::OutputWriter;

const size_t numLines = 100;

struct Agent {
    Stream::UP socket;
    Agent(Stream::UP s) : socket(std::move(s)) {}
    void write(const char *prefix) {
        OutputWriter out(*socket, 32);
        for (size_t i = 0; i < numLines; ++i) {
            out.printf("%s%zu\n", prefix, i);
        }
        out.write("\n");
    }
    void read(const char *prefix) {
        LineReader reader(*socket);
        for (size_t lines = 0; true; ++lines) {
            string line;
            reader.readLine(line);
            if (line.empty()) {
                EXPECT_EQUAL(numLines, lines);
                break;
            }
            EXPECT_EQUAL(strfmt("%s%zu", prefix, lines), line);
        }
    }
};

struct Client : public Agent, public vespalib::Runnable {
    Client(Stream::UP s) : Agent(std::move(s)) {}
    virtual void run() {
        TEST_THREAD("client");
        write("client-");
        read("server-");
    }
};

struct Server : public Agent, public vespalib::Runnable {
    Server(Stream::UP s) : Agent(std::move(s)) {}
    virtual void run() {
        TEST_THREAD("server");
        read("client-");
        write("server-");
    }
};

TEST("socket") {
    ServerSocket serverSocket;
    Client client(Stream::UP(new Socket("localhost", serverSocket.port())));
    Server server(serverSocket.accept());
    vespalib::Thread clientThread(client);
    vespalib::Thread serverThread(server);
    clientThread.start();
    serverThread.start();
    clientThread.join();
    serverThread.join();
    {
        server.socket.reset();
        LineReader reader(*client.socket);
        string line;
        EXPECT_FALSE(reader.readLine(line));
        EXPECT_TRUE(line.empty());
        EXPECT_TRUE(client.socket->eof());
        EXPECT_FALSE(client.socket->tainted());
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
