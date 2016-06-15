// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright (C) 1998-2003 Fast Search & Transfer ASA
// Copyright (C) 2003 Overture Services Norway AS

#include <vespa/fastos/fastos.h>
#include <vespa/searchcore/util/eventloop.h>


double FastS_TimeOut::_val[FastS_TimeOut::valCnt];


void
FastS_TimeOut::WriteTime(char* buffer, size_t bufsize, double xtime)
{
    snprintf(buffer, bufsize, "%.3fs ", xtime);
}
