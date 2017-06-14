// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/document_runnable.h>
#include <vespa/fastos/app.h>
#include <deque>
#include <vector>

class Client;

class Message {
public:
    Message() : timestamp(0), client(0), busy(false), target(0) {};
    Message(const Message& other) : timestamp(other.timestamp), client(other.client), busy(other.busy), target(other.target) {};

    void operator=(const Message& other) { timestamp = other.timestamp; client = other.client; };

    double timestamp;
    Client* client;
    bool busy;
    int target;
};


class Receiver : public document::Runnable
{
public:
    std::deque<Message> queue;
    vespalib::Monitor sync;

    int meanwaitms;
    int processed;
    size_t maxqueuesize;

    Receiver(int meanwait, int max) : meanwaitms(meanwait), processed(0), maxqueuesize(max) {};

    bool enqueue(const Message& msg);
    void run() override;
    void print();
};

class Messaging : public document::Runnable
{
public:
    std::deque<Message> queue;
    std::vector<Receiver*> receivers;
    std::vector<Client*> clients;
    vespalib::Monitor sync;
    FastOS_Time startTime;
    FastOS_Time period;

    int lastOk;
    int meanwaitms;

    Messaging(int meanwait);
    ~Messaging();

    void sendMessage(const Message& m);
    void print();
    void run() override;
};

Messaging::Messaging(int meanwait) : lastOk(0), meanwaitms(meanwait) {}
Messaging::~Messaging() {}

class Client : public document::Runnable {
public:
    vespalib::Monitor sync;

    int ok;
    int failed;
    int busy;
    int pending;
    double windowsize;
    Messaging& messaging;
    int timeout;
    double max_diff;

    virtual void returnMessage(const Message& m) = 0;
    void run() override;
    virtual void print(double timenow);

    Client(Messaging& msgng, double windowSize, int to);
    ~Client();
};

Client::Client(Messaging& msgng, double windowSize, int to)
    : ok(0), failed(0), busy(0), pending(0), windowsize(windowSize), messaging(msgng), timeout(to), max_diff(0)
{}
Client::~Client() {}

class FixedClient : public Client {
public:
    FixedClient(Messaging& msgng, int winsize, int to)
        : Client(msgng, winsize, to) {};
    void returnMessage(const Message& m) override;
};

class LoadBalancingClient : public Client {
public:
    LoadBalancingClient(Messaging& msgng, int winsize, int to);
    void returnMessage(const Message& m) override;
    void run() override;
    void print(double timenow) override;

    std::vector<double> weights;
};

class BusyCounterBalancingClient : public Client {
public:
    BusyCounterBalancingClient(Messaging& msgng, int winsize, int to);
    void returnMessage(const Message& m) override;
    void run() override;
    void print(double timenow) override;

    std::vector<int> busyCount;
};


class DynamicClient : public Client {
public:
    int maxwinsize;
    int threshold;
    double lastFailTimestamp;

    DynamicClient(Messaging& msgng, int maxWinSize, double to)
        : Client(msgng, 1, static_cast<int>(to)), maxwinsize(maxWinSize), threshold(maxWinSize / 2), lastFailTimestamp(0) {};
    void returnMessage(const Message& m) override;
};

class LatencyControlClient : public Client {
public:
    int count;

    LatencyControlClient(Messaging& msgng, double to)
        : Client(msgng, 1, static_cast<int>(to)),
          count(0){};
    void returnMessage(const Message& m) override;
    void print(double timenow) override;
};


class ThrottlingApp : public FastOS_Application
{
private:
    ThrottlingApp(const ThrottlingApp &);
    ThrottlingApp& operator=(const ThrottlingApp &);

public:
    ThrottlingApp() {};

    int Main() override;
};
