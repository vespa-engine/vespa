// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.




static void
bomb(void)
{
    char *p;

    p = nullptr;
    *p = 4;
}

class FastS_Bomber : public FastOS_Runnable
{
   void Run(FastOS_ThreadInterface *thread, void *arg)
    {
        (void) thread;
        (void) arg;
        bomb();
    }
};

static int
bombMain(void)
{
    FastOS_ThreadPool *pool = new FastOS_ThreadPool(128*1024);
    FastS_Bomber bomber;
    FastOS_ThreadInterface *thread;

    thread =  pool->NewThread(&bomber, nullptr);
    if (thread != nullptr)
        thread->Join();

    pool->Close();
    delete pool;
    return (0);
}


class FastS_CoreTestApp : public FastOS_Application
{
public:
  FastS_CoreTestApp(void) { }
  ~FastS_CoreTestApp(void) { }
  int Main(void);
};


int
FastS_CoreTestApp::Main(void)
{

    return bombMain();
}


int
main(int argc, char **argv)
{
  FastS_CoreTestApp app;
  setvbuf(stdout, nullptr, _IOLBF, 8192);
  if (argc == 1)
      return app.Entry(argc, argv);
  else
      return bombMain();
}
