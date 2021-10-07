// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/documentid.h>
#include <iostream>

int main(int argc, char** argv)
{
    if (argc != 2) {
        std::cerr << "Usage: getbucketid <documentid>\n";
        return 1;
    }
    document::BucketIdFactory factory;
    document::BucketId id = factory.getBucketId(document::DocumentId(argv[1]));

    printf("%s has bucketid %s\n", argv[1], id.toString().c_str());
}


