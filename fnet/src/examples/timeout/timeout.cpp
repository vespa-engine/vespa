// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("timeout");
#include <vespa/fnet/fnet.h>


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

    virtual void PerformTask() override;
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
  double                     ms;
  FastOS_Time                t;
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
  t.SetNow();
  timeout.Schedule(2.0); // timeout in 2 seconds

  FastOS_Thread::Sleep(1000);

  timeout.Unschedule(); // cancel timeout
  ms = t.MilliSecsToNow();

  if (queue.GetPacketCnt_NoLock() == 0)
    fprintf(stderr, "timeout canceled; no timeout packet delivered\n");
  fprintf(stderr, "time since timeout was scheduled: %f ms\n", ms);

  fprintf(stderr, "scheduling timeout in 2 seconds...\n");
  t.SetNow();
  timeout.Schedule(2.0); // timeout in 2 seconds

  packet = queue.DequeuePacket(&context); // wait for timeout
  ms = t.MilliSecsToNow();

  if (packet->IsTimeoutCMD())
    fprintf(stderr, "got timeout packet\n");
  fprintf(stderr, "time since timeout was scheduled: %f ms\n", ms);

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
