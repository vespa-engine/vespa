// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/fastlib/text/unicodeutil.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/vsm/searcher/fold.h>
#include <vespa/vsm/searcher/futf8strchrfieldsearcher.h>
#include <vespa/vsm/searcher/utf8stringfieldsearcherbase.h>

using search::byte; // unsigned char

namespace vsm {

template <typename T>
class Vector : public std::vector<T>
{
public:
    Vector() : std::vector<T>() {}
    Vector<T> & a(T v) { this->push_back(v); return *this; }
};

typedef Vector<ucs4_t> UCS4V;
typedef Vector<size_t> SizeV;
typedef UTF8StringFieldSearcherBase SFSB;
typedef FUTF8StrChrFieldSearcher FSFS;

class TextUtilTest : public vespalib::TestApp
{
private:
    ucs4_t getUTF8Char(const char * src);
    template <typename BW, bool OFF>
    void assertSkipSeparators(const char * input, size_t len, const UCS4V & expdstbuf, const SizeV & expoffsets);
    void assertAnsiFold(const std::string & toFold, const std::string & exp);
    void assertAnsiFold(char c, char exp);
    void assert_sse2_foldua(const std::string & toFold, size_t charFolded, const std::string & exp);
    void assert_sse2_foldua(unsigned char c, unsigned char exp, size_t charFolded = 16);

    template <typename BW, bool OFF>
    void testSkipSeparators();
    void testSkipSeparators();
    void testSeparatorCharacter();
    void testAnsiFold();
    void test_lfoldua();
    void test_sse2_foldua();

public:
    int Main() override;
};

ucs4_t
TextUtilTest::getUTF8Char(const char * src)
{
    ucs4_t retval = Fast_UnicodeUtil::GetUTF8Char(src);
    ASSERT_TRUE(retval != Fast_UnicodeUtil::_BadUTF8Char);
    return retval;
}

template <typename BW, bool OFF>
void
TextUtilTest::assertSkipSeparators(const char * input, size_t len, const UCS4V & expdstbuf, const SizeV & expoffsets)
{
    const byte * srcbuf = reinterpret_cast<const byte *>(input);
    auto dstbuf = std::make_unique<ucs4_t[]>(len + 1);
    auto offsets = std::make_unique<size_t[]>(len + 1);
    UTF8StrChrFieldSearcher fs;
    BW bw(dstbuf.get(), offsets.get());
    size_t dstlen = fs.skipSeparators(srcbuf, len, bw);
    EXPECT_EQUAL(dstlen, expdstbuf.size());
    ASSERT_TRUE(dstlen == expdstbuf.size());
    for (size_t i = 0; i < dstlen; ++i) {
        EXPECT_EQUAL(dstbuf[i], expdstbuf[i]);
        if (OFF) {
            EXPECT_EQUAL(offsets[i], expoffsets[i]);
        }
    }
}

void
TextUtilTest::assertAnsiFold(const std::string & toFold, const std::string & exp)
{
    char folded[256];
    EXPECT_TRUE(FSFS::ansiFold(toFold.c_str(), toFold.size(), folded));
    EXPECT_EQUAL(std::string(folded, toFold.size()), exp);
}

void
TextUtilTest::assertAnsiFold(char c, char exp)
{
    char folded;
    EXPECT_TRUE(FSFS::ansiFold(&c, 1, &folded));
    EXPECT_EQUAL((int32_t)folded, (int32_t)exp);
}

void
TextUtilTest::assert_sse2_foldua(const std::string & toFold, size_t charFolded, const std::string & exp)
{
    char folded[256];
    size_t alignedStart =  0xF - (size_t(folded + 0xF) % 0x10);
    const unsigned char * toFoldOrg = reinterpret_cast<const unsigned char *>(toFold.c_str());
    const unsigned char * retval =
        sse2_foldua(toFoldOrg, toFold.size(), reinterpret_cast<unsigned char *>(folded + alignedStart));
    EXPECT_EQUAL((size_t)(retval - toFoldOrg), charFolded);
    EXPECT_EQUAL(std::string(folded + alignedStart, charFolded), exp);
}

void
TextUtilTest::assert_sse2_foldua(unsigned char c, unsigned char exp, size_t charFolded)
{
    unsigned char toFold[16];
    memset(toFold, c, 16);
    unsigned char folded[32];
    size_t alignedStart =  0xF - (size_t(folded + 0xF) % 0x10);
    const unsigned char * retval = sse2_foldua(toFold, 16, folded + alignedStart);
    EXPECT_EQUAL((size_t)(retval - toFold), charFolded);
    for (size_t i = 0; i < charFolded; ++i) {
        EXPECT_EQUAL((int32_t)folded[i + alignedStart], (int32_t)exp);
    }
}

template <typename BW, bool OFF>
void
TextUtilTest::testSkipSeparators()
{
    // ascii characters
    assertSkipSeparators<BW, OFF>("foo",    3, UCS4V().a('f').a('o').a('o'),  SizeV().a(0).a(1).a(2));
    assertSkipSeparators<BW, OFF>("f\x1Fo", 3, UCS4V().a('f').a('o'),         SizeV().a(0).a(2));
    assertSkipSeparators<BW, OFF>("f\no",   3, UCS4V().a('f').a('\n').a('o'), SizeV().a(0).a(1).a(2));
    assertSkipSeparators<BW, OFF>("f\to",   3, UCS4V().a('f').a('\t').a('o'), SizeV().a(0).a(1).a(2));

    // utf8 char
    assertSkipSeparators<BW, OFF>("\xC2\x80\x66",         3, UCS4V().a(getUTF8Char("\xC2\x80")).a('f'),
                                                             SizeV().a(0).a(2));
    assertSkipSeparators<BW, OFF>("\xE0\xA0\x80\x66",     4, UCS4V().a(getUTF8Char("\xE0\xA0\x80")).a('f'),
                                                             SizeV().a(0).a(3));
    assertSkipSeparators<BW, OFF>("\xF0\x90\x80\x80\x66", 5, UCS4V().a(getUTF8Char("\xF0\x90\x80\x80")).a('f'),
                                                             SizeV().a(0).a(4));

    // replacement string (sharp s -> ss)
    assertSkipSeparators<BW, OFF>("\xC3\x9F\x66\xC3\x9F", 5, UCS4V().a('s').a('s').a('f').a('s').a('s'),
                                                             SizeV().a(0).a(0).a(2).a(3).a(3));
}

void
TextUtilTest::testSkipSeparators()
{
    Fast_NormalizeWordFolder::Setup(Fast_NormalizeWordFolder::DO_SHARP_S_SUBSTITUTION);

    testSkipSeparators<SFSB::BufferWrapper, false>();
    testSkipSeparators<SFSB::OffsetWrapper, true>();
}

void
TextUtilTest::testSeparatorCharacter()
{
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x00'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x01'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x02'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x03'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x04'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x05'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x06'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x07'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x08'));
    EXPECT_TRUE(! SFSB::isSeparatorCharacter('\x09')); // '\t'
    EXPECT_TRUE(! SFSB::isSeparatorCharacter('\x0a')); // '\n'
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x0b'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x0c'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x0d'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x0e'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x0f'));

    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x10'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x11'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x12'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x13'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x14'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x15'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x16'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x17'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x18'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x19'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x1a'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x1b'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x1c'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x1d'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x1e'));
    EXPECT_TRUE(SFSB::isSeparatorCharacter('\x1f'));

    EXPECT_TRUE(! SFSB::isSeparatorCharacter('\x20')); // space
}

void
TextUtilTest::testAnsiFold()
{
    FieldSearcher::init();
    assertAnsiFold("", "");
    assertAnsiFold("ABCDEFGHIJKLMNOPQRSTUVWXYZ", "abcdefghijklmnopqrstuvwxyz");
    assertAnsiFold("abcdefghijklmnopqrstuvwxyz", "abcdefghijklmnopqrstuvwxyz");
    assertAnsiFold("0123456789", "0123456789");
    for (int i = 0; i < 128; ++i) {
        if ((i >= 'a' && i <= 'z') || (i >= '0' && i <= '9')) {
            assertAnsiFold(i, i);
        } else if (i >= 'A' && i <= 'Z') {
            assertAnsiFold(i, i + 32);
        } else {
            assertAnsiFold(i, 0);
        }
    }

    // non-ascii is ignored
    for (int i = 128; i < 256; ++i) {
        char toFold = i;
        char folded;
        EXPECT_TRUE(!FSFS::ansiFold(&toFold, 1, &folded));
    }
}

void
TextUtilTest::test_lfoldua()
{
    FieldSearcher::init();
    char folded[256];
    size_t alignedStart = 0;
    const char * toFold = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    size_t len = strlen(toFold);
    EXPECT_TRUE(FSFS::lfoldua(toFold, len, folded, alignedStart));
    EXPECT_EQUAL(std::string(folded + alignedStart, len), "abcdefghijklmnopqrstuvwxyz");
}

void
TextUtilTest::test_sse2_foldua()
{
    assert_sse2_foldua("", 0, "");
    assert_sse2_foldua("ABCD", 0, "");
    assert_sse2_foldua("ABCDEFGHIJKLMNO",   0,  "");
    assert_sse2_foldua("ABCDEFGHIJKLMNOP",  16, "abcdefghijklmnop");
    assert_sse2_foldua("ABCDEFGHIJKLMNOPQ", 16, "abcdefghijklmnop");
    assert_sse2_foldua("KLMNOPQRSTUVWXYZ",  16, "klmnopqrstuvwxyz");
    assert_sse2_foldua("abcdefghijklmnop",  16, "abcdefghijklmnop");
    assert_sse2_foldua("klmnopqrstuvwxyz",  16, "klmnopqrstuvwxyz");
    assert_sse2_foldua("0123456789abcdef",  16, "0123456789abcdef");

    for (int i = 0; i < 128; ++i) {
        if ((i >= 'a' && i <= 'z') || (i >= '0' && i <= '9')) {
            assert_sse2_foldua(i, i);
        } else if (i >= 'A' && i <= 'Z') {
            assert_sse2_foldua(i, i + 32);
        } else {
            assert_sse2_foldua(i, 0);
        }
    }

    // non-ascii is ignored
    for (int i = 128; i < 256; ++i) {
        assert_sse2_foldua(i, '?', 0);
    }
}

int
TextUtilTest::Main()
{
    TEST_INIT("textutil_test");

    testSkipSeparators();
    testSeparatorCharacter();
    testAnsiFold();
    test_lfoldua();
    test_sse2_foldua();

    TEST_DONE();
}

}

TEST_APPHOOK(vsm::TextUtilTest);
