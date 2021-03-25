// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.logging;

import java.io.IOException;
import java.io.OutputStream;

interface LogWriter <LOGTYPE>  {
    void write(LOGTYPE record, OutputStream outputStream) throws IOException;
}
