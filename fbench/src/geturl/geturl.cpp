// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/net/crypto_engine.h>
#include <httpclient/httpclient.h>
#include <iostream>

int
main(int argc, char** argv)
{
    if (argc != 4) {
        printf("usage: vespa-fbench-geturl <host> <port> <url>\n");
        return -1;
    }

    auto engine = std::make_shared<vespalib::NullCryptoEngine>();
    HTTPClient  client(engine, argv[1], atoi(argv[2]), false, false);
    if (!client.Fetch(argv[3], &std::cout).Ok()) {
        fprintf(stderr, "geturl: could not fetch 'http://%s:%d%s'\n",
                argv[1], atoi(argv[2]), argv[3]);
        return -1;
    }
    return 0;
}
