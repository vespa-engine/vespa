// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <grouping_test/hello-world-lib/hello-world.h>

class App : public FastOS_Application
{
public:
    int Main();
};

int
App::Main()
{
    HelloWorld::print();
    fprintf(stdout, "C++/app/Hello World\n");
    return 0;
}

int
main(int argc, char **argv)
{
    App myapp;
    return myapp.Entry(argc, argv);
}
