// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.




static void
bomb(void)
{
    char *p;

    p = NULL;
    *p = 4;
}

void *BomberRun(void *arg)
{
    (void) arg;
    bomb();
    return NULL;
};

static int
bombMain(void)
{
    pthread_t thread;
    void *ret;

    (void) pthread_create(&thread, NULL, BomberRun, NULL);
    ret = NULL;
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
