# Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
prog cppserver 1 "tcp/$CPP_PORT" "$SIMPLESERVER"
prog javaserver 1 "tcp/$JAVA_PORT" "$BINREF/runjava SimpleServer"
