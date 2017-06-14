// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/config/subscription/configuri.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/memfilepersistence/tools/dumpslotfile.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/util/programoptions_testutils.h>
#include <tests/spi/memfiletestutils.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/document/config/config-documenttypes.h>

namespace storage {
namespace memfile {

class DumpSlotFileTest : public SingleDiskMemFileTestUtils
{
    CPPUNIT_TEST_SUITE(DumpSlotFileTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();

public:
    void testSimple();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DumpSlotFileTest);

#define ASSERT_MATCH(optstring, pattern) \
{ \
    vespalib::AppOptions opts("dumpslotfile " optstring); \
    std::ostringstream out; \
    config::ConfigUri configUri(config::ConfigUri::createFromInstance( \
            document::TestDocRepo::getDefaultConfig())); \
    std::unique_ptr<document::DocumenttypesConfig> config = config::ConfigGetter<document::DocumenttypesConfig>::getConfig(configUri.getConfigId(), configUri.getContext()); \
    SlotFileDumper::dump(opts.getArgCount(), opts.getArguments(), \
                         configUri, out, out); \
    CPPUNIT_ASSERT_MATCH_REGEX(pattern, out.str()); \
    output = out.str(); \
}

void
DumpSlotFileTest::testSimple()
{
    std::string output;
        // Test syntax page
    ASSERT_MATCH("--help", ".*Usage: dumpslotfile.*");
        // Test non-existing file. (Handle as empty file)
    ASSERT_MATCH("00a.0",
                 ".*BucketId\\(0x000000000000000a\\)"
                 ".*document count: 0.*non-existing.*");
        // Parse bucketid without extension.
    ASSERT_MATCH("000000000000000a",
                 ".*BucketId\\(0x000000000000000a\\) "
                 "\\(extracted from filename\\).*");
        // Parse invalid bucket id.
    ASSERT_MATCH("000010000000000g",
                 ".*Failed to extract bucket id from filename.*");
        // Test toXml with no data. Thus doesn't require doc config
    ASSERT_MATCH("--toxml --documentconfig whatevah 000a.0",
                 ".*<vespafeed>.*");
        // Test invalid arguments
    ASSERT_MATCH("--foobar", ".*Invalid option 'foobar'\\..*");
        // What to show in XML doesn't make sense in non-xml mode
    ASSERT_MATCH("--includeremoveddocs 0.0",
                 ".*Options for what to include in XML makes no sense when not "
                 "printing XML content.*");
    ASSERT_MATCH("--includeremoveentries 0.0",
                 ".*Options for what to include in XML makes no sense when not "
                 "printing XML content.*");
        // To binary only works for single doc
    ASSERT_MATCH("--tobinary 0.0",
                 ".*To binary option only works for a single document.*");

    BucketId bid(1, 0);
    createTestBucket(bid, 0);
    ASSERT_MATCH("-nN vdsroot/disks/d0/400000000000000.0",
                 ".*"
                 "Unique document count: 8.*"
                 "Total document size: [0-9]+.*"
                 "Used size: [0-9]+.*"
                 "Filename: .*/d0/.*"
                 "Filesize: 12288.*"
                 "SlotFileHeader.*"
                 "[0-9]+ empty entries.*"
                 "Header block.*"
                 "Content block.*"
                 "Slotfile verified.*"
    );
    ASSERT_MATCH("vdsroot/disks/d0/400000000000000.0", ".*ff ff ff ff.*");

        // User friendly output
    ASSERT_MATCH("--friendly -nN vdsroot/disks/d0/400000000000000.0",
                 ".*id:mail:testdoctype1:n=0:9380.html.*");

    ASSERT_MATCH("--tobinary "
                 "--docid id:mail:testdoctype1:n=0:doesnotexisthere.html "
                 "vdsroot/disks/d0/400000000000000.0",
                 ".*No document with id id:mail:testdoctype1:n=0:doesnotexi.* "
                 "found.*");

    // Should test XML with content.. But needs document config for it to work.
    // Should be able to create programmatically from testdocman.
    ASSERT_MATCH("--toxml --documentconfig '' "
                 "vdsroot/disks/d0/400000000000000.0",
                 ".*<vespafeed>\n"
                 "<document documenttype=\"testdoctype1\" "
                    "documentid=\"id:mail:testdoctype1:n=0:9639.html\">\n"
                 "<content>overwritten</content>\n"
                 "</document>.*");

        // To binary
    ASSERT_MATCH("--tobinary --docid id:mail:testdoctype1:n=0:9380.html "
                 "vdsroot/disks/d0/400000000000000.0",
                 ".*");
    {
        TestDocMan docMan;
        document::ByteBuffer buf(output.c_str(), output.size());
        document::Document doc(docMan.getTypeRepo(), buf);
        CPPUNIT_ASSERT_EQUAL(std::string(
           "<document documenttype=\"testdoctype1\" "
           "documentid=\"id:mail:testdoctype1:n=0:9380.html\">\n"
           "<content>To be, or not to be: that is the question:\n"
           "Whether 'tis nobler in the mind to suffer\n"
           "The slings and arrows of outrage</content>\n"
           "</document>"), doc.toXml());
    }

        // Fail verification
    {
        vespalib::LazyFile file("vdsroot/disks/d0/400000000000000.0", 0);
        file.write("corrupt", 7, 64);
    }
    ASSERT_MATCH("-nN vdsroot/disks/d0/400000000000000.0",
                 ".*lot 0 at timestamp [0-9]+ failed checksum verification.*");
}

} // memfile
} // storage
