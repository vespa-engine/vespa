// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/controlpacket.h>
#include <vespa/fnet/packetqueue.h>
#include <vespa/fnet/signalshutdown.h>
#include <vespa/fnet/transport.h>
#include <vespa/vespalib/util/signalhandler.h>
#include <vespa/vespalib/util/time.h>

#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("timeout");

class Timeout : public FNET_Task {
private:
    FNET_PacketQueue* _queue;

    Timeout(const Timeout&);
    Timeout& operator=(const Timeout&);

public:
    Timeout(FNET_Scheduler* scheduler, FNET_PacketQueue* queue) : FNET_Task(scheduler), _queue(queue) {}

    void PerformTask() override;
};

void Timeout::PerformTask() { _queue->QueuePacket(&FNET_ControlPacket::Timeout, FNET_Context()); }

class MyApp {
public:
    int main(int argc, char** argv);
};

int MyApp::main(int, char**) {
    using clock = std::chrono::steady_clock;
    using ms_double = std::chrono::duration<double, std::milli>;

    ms_double         ms;
    clock::time_point t;
    FNET_PacketQueue  queue;
    FNET_Transport    transport;
    Timeout           timeout(transport.GetScheduler(), &queue);
    transport.Start();

    // stable-state operation
    std::this_thread::sleep_for(100ms);

    FNET_Packet* packet;
    FNET_Context context;

    fprintf(stderr, "scheduling timeout in 1 seconds...\n");
    t = clock::now();
    timeout.Schedule(1.0); // timeout in 1 seconds

    std::this_thread::sleep_for(100ms);

    timeout.Unschedule(); // cancel timeout
    ms = (clock::now() - t);

    if (queue.GetPacketCnt_NoLock() == 0)
        fprintf(stderr, "timeout canceled; no timeout packet delivered\n");
    fprintf(stderr, "time since timeout was scheduled: %f ms\n", ms.count());

    fprintf(stderr, "scheduling timeout in 1 seconds...\n");
    t = clock::now();
    timeout.Schedule(1.0); // timeout in 1 seconds

    packet = queue.DequeuePacket(&context); // wait for timeout
    ms = (clock::now() - t);

    if (packet->IsTimeoutCMD())
        fprintf(stderr, "got timeout packet\n");
    fprintf(stderr, "time since timeout was scheduled: %f ms\n", ms.count());

    transport.ShutDown(true);
    return 0;
}

int main(int argc, char** argv) {
    vespalib::SignalHandler::PIPE.ignore();
    MyApp myapp;
    return myapp.main(argc, argv);
}
