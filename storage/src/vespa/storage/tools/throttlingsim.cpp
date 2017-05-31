// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "throttlingsim.h"
#include <algorithm>
#include <vespa/vespalib/util/stringfmt.h>

bool Receiver::enqueue(const Message& msg) {
    vespalib::MonitorGuard lock(sync);
    if (queue.size() < maxqueuesize) {
        queue.push_back(msg);
        lock.broadcast();
        return true;
    } else {
        return false;
    }
}

void Receiver::run() {
    while (running()) {
        vespalib::MonitorGuard lock(sync);
        if (!queue.empty()) {
            Message m = queue.front();
            queue.pop_front();
            lock.unlock();

            int maxwaittime = (int)(meanwaitms * 1.5);
            int minwaittime = (int)(meanwaitms * 0.5);
            int wait = random() % (maxwaittime - minwaittime) + minwaittime;
            processed++;

            FastOS_Thread::Sleep(wait);
            m.client->returnMessage(m);
        } else {
            lock.wait();
        }
    }
}

void Receiver::print()
{
    vespalib::MonitorGuard lock(sync);
    fprintf(stderr, "Proc time %d, Processed %d, Queue size: %d\n", meanwaitms, processed, (int)queue.size());
}

void Messaging::sendMessage(const Message& m)
{
    vespalib::MonitorGuard lock(sync);
    queue.push_back(m);
    lock.broadcast();
}

void Messaging::run()
{
    while (running()) {
        vespalib::MonitorGuard lock(sync);
        if (!queue.empty()) {
            Message m = queue.front();

            FastOS_Time tm;
            tm.SetNow();
            double timestamp = tm.MicroSecs() / 1000;

            double wait = m.timestamp - timestamp + meanwaitms;

            if (wait > 0) {
                lock.unlock();
                FastOS_Thread::Sleep(static_cast<int>(wait));
                continue;
            }

            queue.pop_front();
            if (!receivers[m.target]->enqueue(m)) {
                m.busy = true;
                lock.unlock();
                m.client->returnMessage(m);
            }
        } else {
            lock.wait();
        }
    }
}

void Messaging::print()
{
    double startT = startTime.MilliSecsToNow();
    double per = period.MilliSecsToNow();

    fprintf(stderr, "\n\n"
           "Statistics after %G milliseconds\n"
           "--------------------------------------------------\n",
           startT);

    for (size_t i = 0; i < receivers.size(); i++) {
        fprintf(stderr, "Server %ld\t", i);
        receivers[i]->print();
    }

    fprintf(stderr, "--------------------------------------------------\n");

    int ok = 0;
    int failed = 0;
    for (size_t i = 0; i < clients.size(); i++) {
        ok += clients[i]->ok;
        failed += clients[i]->failed;
        fprintf(stderr, "Client %ld\t", i);
        clients[i]->print(startT);
    }

    fprintf(stderr, "\nThroughput last period %G docs/second\n", 1000 * (ok - lastOk) / per);
    fprintf(stderr, "Throughput %G docs/second\n", 1000 * (ok / startT));

    if (ok + failed > 0) {
        fprintf(stderr, "Total OK %d, total failed %d, %% failed %G\n", ok, failed, (100 * (double)failed) / (double)(ok + failed));
    }

    lastOk = ok;
}


void Client::run() {
    while (running()) {
        {
            vespalib::MonitorGuard lock(sync);

            if (pending < windowsize) {
                Message m;

                FastOS_Time tm;
                tm.SetNow();
                m.timestamp = tm.MicroSecs() / 1000;

                m.client = this;
                m.target = random() % messaging.receivers.size();
                messaging.sendMessage(m);
                pending++;
            }
        }
        FastOS_Thread::Sleep(2);
    }
}

void Client::print(double timenow)
{
    vespalib::MonitorGuard lock(sync);
    fprintf(stderr, "Ok %d, failures %d, busy %d, pending %d, windowsize %G, throughput %G max_diff %G\n", ok, failed, busy, pending, windowsize, 1000 * ok/timenow, max_diff);
}

void FixedClient::returnMessage(const Message& m) {
    vespalib::MonitorGuard lock(sync);

    pending--;

    FastOS_Time tm;
    tm.SetNow();
    double timestamp = tm.MicroSecs() / 1000;
    double diff = timestamp - m.timestamp;

    if (m.busy) {
        busy++;
    } else if (diff < timeout) {
        ok++;
    } else {
        failed++;
    }

    max_diff = std::max(diff, max_diff);

    lock.broadcast();
}

LoadBalancingClient::LoadBalancingClient(Messaging& msgng, int winsize, int to)
        : Client(msgng, winsize, to)
{
    for (uint32_t i = 0 ; i < msgng.receivers.size(); i++) {
        weights.push_back(1.0);
    }
};


void LoadBalancingClient::run() {
    while (running()) {
        {
            vespalib::MonitorGuard lock(sync);

            if (pending < windowsize) {
                Message m;

                FastOS_Time tm;
                tm.SetNow();
                m.timestamp = tm.MicroSecs() / 1000;

                m.client = this;

                double sum = 0;
                for (uint32_t i = 0; i < weights.size(); i++) {
                    sum += weights[i];
                }

                float r = sum * (float)random()/(float)RAND_MAX;

                double curr = 0;
                for (uint32_t i = 0; i < weights.size(); i++) {
                    curr += weights[i];

                    if (curr >= r) {
                        m.target = i;
                        break;
                    }

                }

                messaging.sendMessage(m);
                pending++;
            }
        }
        FastOS_Thread::Sleep(2);
    }
}

void LoadBalancingClient::print(double timenow)
{
    vespalib::MonitorGuard lock(sync);

    std::string s;
    for (uint32_t i = 0; i < weights.size(); i++) {
        s += vespalib::make_string("%G ", weights[i]);
    }
    fprintf(stderr, "Ok %d, failures %d, busy %d, pending %d, windowsize %G, throughput %G max_diff %G\n   Weights: [ %s]\n", ok, failed, busy, pending, windowsize, 1000 * ok/timenow, max_diff, s.c_str());

}

void LoadBalancingClient::returnMessage(const Message& m) {
    vespalib::MonitorGuard lock(sync);

    pending--;

    FastOS_Time tm;
    tm.SetNow();
    double timestamp = tm.MicroSecs() / 1000;
    double diff = timestamp - m.timestamp;

    if (m.busy) {
        weights[m.target] -= 0.01;

        for (uint32_t i = 1; i < weights.size(); i++) {
            weights[i] = weights[i] / weights[0];
        }
        weights[0] = 1.0;

        busy++;
    } else if (diff < timeout) {
        ok++;
    } else {
        failed++;
    }

    max_diff = std::max(diff, max_diff);

    lock.broadcast();
}

BusyCounterBalancingClient::BusyCounterBalancingClient(Messaging& msgng, int winsize, int to)
        : Client(msgng, winsize, to)
{
    for (uint32_t i = 0 ; i < msgng.receivers.size(); i++) {
        busyCount.push_back(0);
    }
};


void BusyCounterBalancingClient::run() {
    // int startTime = time(NULL);

    while (running()) {
        {
            vespalib::MonitorGuard lock(sync);

            if (pending < windowsize) {
                Message m;
                FastOS_Time tm;
                tm.SetNow();
                m.timestamp = tm.MicroSecs() / 1000;

                m.client = this;

                m.target = 0;
                for (uint32_t i = 1; i < busyCount.size(); i++) {
                    if (busyCount[i] < busyCount[m.target]) {
                        m.target = i;
                    }
                }

                messaging.sendMessage(m);
                pending++;
            }
        }

        FastOS_Thread::Sleep(3);
    }
}

void BusyCounterBalancingClient::print(double timenow)
{
    vespalib::MonitorGuard lock(sync);

    std::string s;
    for (uint32_t i = 0; i < busyCount.size(); i++) {
        s += vespalib::make_string("%d ", busyCount[i]);
    }
    fprintf(stderr, "Ok %d, failures %d, busy %d, pending %d, windowsize %G, throughput %G max_diff %G\n   BusyCount: [ %s]\n", ok, failed, busy, pending, windowsize, 1000 * ok/timenow, max_diff, s.c_str());

}

void BusyCounterBalancingClient::returnMessage(const Message& m) {
    vespalib::MonitorGuard lock(sync);

    pending--;

    FastOS_Time tm;
    tm.SetNow();
    double timestamp = tm.MicroSecs() / 1000;
    double diff = timestamp - m.timestamp;

    if (m.busy) {
        busyCount[m.target]++;
        busy++;
    } else if (diff < timeout) {
        ok++;
    } else {
        failed++;
    }

    max_diff = std::max(diff, max_diff);

    lock.broadcast();
}



void DynamicClient::returnMessage(const Message& m) {
    vespalib::MonitorGuard lock(sync);

    pending--;

    FastOS_Time tm;
    tm.SetNow();
    double timestamp = tm.MicroSecs() / 1000;
    double diff = timestamp - m.timestamp;

    if (diff < timeout) {
        ok++;
    } else {
        //ffprintf(stderr, stderr, "Message took %G ms to process, more than %G\n", diff, timeout);
        failed++;
    }

    if (diff < timeout / 2) {
        if (windowsize < maxwinsize) {
            if (windowsize > threshold) {
                windowsize += (1/windowsize);
            } else {
                windowsize++;
            }
        }
    } else if (m.timestamp > lastFailTimestamp) {
        threshold = std::max(2, (int)(windowsize / 2));
        windowsize = 1;
        lastFailTimestamp = timestamp;
    }

    lock.broadcast();
}

void LatencyControlClient::returnMessage(const Message& m) {
    vespalib::MonitorGuard lock(sync);

    pending--;

    FastOS_Time tm;
    tm.SetNow();
    double timestamp = tm.MicroSecs() / 1000;
    double diff = timestamp - m.timestamp;

    if (diff < timeout) {
        ok++;
    } else {
        //ffprintf(stderr, stderr, "Message took %G ms to process, more than %G\n", diff, timeout);
        failed++;
    }

    max_diff = std::max(diff, max_diff);

    ++count;

    if(count >= windowsize) {
        if (max_diff < timeout/4) {
            windowsize+=10;
        }
        if (timeout/4 <= max_diff && max_diff <= timeout/1.5) {
            ++windowsize;
        }
        if (max_diff > timeout/1.5) {
            windowsize= std::max(1.0, 0.66*windowsize);
        }
        max_diff = 0;
        count = 0;
    }

    lock.broadcast();
}

void LatencyControlClient::print(double timenow)
{
    vespalib::MonitorGuard lock(sync);
    fprintf(stderr, "Ok %d, failures %d, pending %d, busy %d, windowsize %G, throu %G max_diff %G\n", ok, failed, pending, busy, windowsize, 1000 * ok/timenow, max_diff);
}

int
ThrottlingApp::Main()
{
    FastOS_ThreadPool threadPool(512*1024);
    Messaging m(5);

    m.start(threadPool);
    m.startTime.SetNow();

    for (int i = 0; i < 3; i++) {
        Receiver* r = new Receiver(20, 16);
        r->start(threadPool);
        m.receivers.push_back(r);
    }

    for (int i = 0; i < 3; i++) {
        Receiver* r = new Receiver(60, 16);
        r->start(threadPool);
        m.receivers.push_back(r);
    }

    {
        BusyCounterBalancingClient* c = new BusyCounterBalancingClient(m, 400, 5000);
        c->start(threadPool);
        m.clients.push_back(c);
    }
/*
  {
        LoadBalancingClient* c = new LoadBalancingClient(m, 400, 5000);
        c->start(threadPool);
        m.clients.push_back(c);
    }
*/
/*
    {
        FixedClient* c = new FixedClient(m, 400, 5000);
        c->start(threadPool);
        m.clients.push_back(c);
    }
*/
    int timeNow = time(NULL);

    while (time(NULL) - timeNow < 240) {
        m.print();
        m.period.SetNow();
        sleep(2);
    }

    exit(0);
}

int main(int argc, char** argv)
{
    ThrottlingApp app;
    return app.Entry(argc, argv);
}

