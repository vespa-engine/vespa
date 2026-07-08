// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "context.h"

#include <cstdio>

void FNET_Context::Print(uint32_t indent) {
    printf("%*sFNET_Context {\n", indent, "");
    printf("%*s  Value[INT]  : %d\n", indent, "", _value.INT);
    printf("%*s  Value[VOIDP]: %p\n", indent, "", _value.VOIDP);
    printf("%*s}\n", indent, "");
}
