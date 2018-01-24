// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.task.util.process;// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import java.nio.file.Path;
import java.util.List;

public interface ProcessApi {
    ChildProcessImpl spawn(List<String> args, Path outFile);
}
