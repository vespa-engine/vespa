// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "time_boxer.h"

#if 0

#include <time.h>
#include <unistd.h>
#include <stdio.h>

int main(int, char **)
{
    vespalib::TimeBoxer tb(3.0);
    while (tb.hasTimeLeft()) {
        double tl = tb.timeLeft();
        printf("time left: %g seconds\n", tl);
        sleep(1);
    }
}

#endif
