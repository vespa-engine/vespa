// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/fnet.h>
#include <vespa/fastos/app.h>
#include <chrono>

#include <vespa/log/log.h>
LOG_SETUP("timeout");

class Timeout : public FNET_Task
{
private:
  FNET_PacketQueue *_queue;

  Timeout(const Timeout &);
  Timeout &operator=(const Timeout &);

public:
  Timeout(FNET_Scheduler *scheduler,
          FNET_PacketQueue *queue)
    : FNET_Task(scheduler),
      _queue(queue)
  {}

     void PerformTask() override;
};


void
Timeout::PerformTask()
{
    _queue->QueuePacket(&FNET_ControlPacket::Timeout, FNET_Context());
}


class MyApp : public FastOS_Application
{
public:
  int Main() override;
};


int
MyApp::Main()
{
  using clock = std::chrono::steady_clock;
  using ms_double = std::chrono::duration<double,std::milli>;

  ms_double                  ms;
  clock::time_point          t;
  FNET_PacketQueue           queue;
  FastOS_ThreadPool          pool(65000);
  FNET_Transport             transport;
  Timeout                    timeout(transport.GetScheduler(), &queue);
  transport.Start(&pool);

  // stable-state operation
  FastOS_Thread::Sleep(500);

  FNET_Packet  *packet;
  FNET_Context  context;

  fprintf(stderr, "scheduling timeout in 2 seconds...\n");
  t = clock::now();
  timeout.Schedule(2.0); // timeout in 2 seconds

  FastOS_Thread::Sleep(1000);

  timeout.Unschedule(); // cancel timeout
  ms = (clock::now() - t);

  if (queue.GetPacketCnt_NoLock() == 0)
    fprintf(stderr, "timeout canceled; no timeout packet delivered\n");
  fprintf(stderr, "time since timeout was scheduled: %f ms\n", ms.count());

  fprintf(stderr, "scheduling timeout in 2 seconds...\n");
  t = clock::now();
  timeout.Schedule(2.0); // timeout in 2 seconds

  packet = queue.DequeuePacket(&context); // wait for timeout
  ms = (clock::now() - t);

  if (packet->IsTimeoutCMD())
    fprintf(stderr, "got timeout packet\n");
  fprintf(stderr, "time since timeout was scheduled: %f ms\n", ms.count());

  transport.ShutDown(true);
  pool.Close();
  return 0;
}


int
main(int argc, char **argv)
{
  MyApp myapp;
  return myapp.Entry(argc, argv);
}
