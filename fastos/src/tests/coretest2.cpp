// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.




static void
bomb(void)
{
    char *p;

    p = nullptr;
    *p = 4;
}

void *BomberRun(void *arg)
{
    (void) arg;
    bomb();
    return nullptr;
};

static int
bombMain(void)
{
    pthread_t thread;
    void *ret;

    (void) pthread_create(&thread, nullptr, BomberRun, nullptr);
    ret = nullptr;
    (void) pthread_join(thread, &ret);
    return (0);
}


int
main(int argc, char **argv)
{
    (void) argc;
    (void) argv;
    return bombMain();
}
