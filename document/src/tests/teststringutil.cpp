// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/util/stringutil.h>
#include <gtest/gtest.h>

using namespace document;
using vespalib::string;

TEST(StringUtilTest, test_escape)
{
    EXPECT_EQ(string("abz019ABZ"), StringUtil::escape("abz019ABZ"));
    EXPECT_EQ(string("\\t"), StringUtil::escape("\t"));
    EXPECT_EQ(string("\\n"), StringUtil::escape("\n"));
    EXPECT_EQ(string("\\r"), StringUtil::escape("\r"));
    EXPECT_EQ(string("\\\""), StringUtil::escape("\""));
    EXPECT_EQ(string("\\f"), StringUtil::escape("\f"));
    EXPECT_EQ(string("\\\\"), StringUtil::escape("\\"));
    EXPECT_EQ(string("\\x05"), StringUtil::escape("\x05"));
    EXPECT_EQ(string("\\tA\\ncombined\\r\\x055test"),
              StringUtil::escape("\tA\ncombined\r\x05""5test"));
    EXPECT_EQ(string("A\\x20space\\x20separated\\x20string"),
              StringUtil::escape("A space separated string", ' '));
}

TEST(StringUtilTest, test_unescape)
{
    EXPECT_EQ(string("abz019ABZ"),
              StringUtil::unescape("abz019ABZ"));
    EXPECT_EQ(string("\t"), StringUtil::unescape("\\t"));
    EXPECT_EQ(string("\n"), StringUtil::unescape("\\n"));
    EXPECT_EQ(string("\r"), StringUtil::unescape("\\r"));
    EXPECT_EQ(string("\""), StringUtil::unescape("\\\""));
    EXPECT_EQ(string("\f"), StringUtil::unescape("\\f"));
    EXPECT_EQ(string("\\"), StringUtil::unescape("\\\\"));
    EXPECT_EQ(string("\x05"), StringUtil::unescape("\\x05"));
    EXPECT_EQ(string("\tA\ncombined\r\x05""5test"),
              StringUtil::unescape("\\tA\\ncombined\\r\\x055test"));
    EXPECT_EQ(string("A space separated string"),
              StringUtil::unescape("A\\x20space\\x20separated\\x20string"));
}

TEST(StringUtilTest, test_printAsHex)
{
    std::vector<char> asciitable(256);
    for (uint32_t i=0; i<256; ++i) asciitable[i] = i;
    std::ostringstream ost;
    ost << "\n  ";
    StringUtil::printAsHex(ost, &asciitable[0], asciitable.size(),
                           16, true, "  ");
    std::string expected("\n"
                         "    0: 00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f\n"
                         "   16: 10 11 12 13 14 15 16 17 18 19 1a 1b 1c 1d 1e 1f\n"
                         "   32: 20  !  \"  #  $  %  &  '  (  )  *  +  ,  -  .  /\n"
                         "   48:  0  1  2  3  4  5  6  7  8  9  :  ;  <  =  >  ?\n"
                         "   64:  @  A  B  C  D  E  F  G  H  I  J  K  L  M  N  O\n"
                         "   80:  P  Q  R  S  T  U  V  W  X  Y  Z  [  \\  ]  ^  _\n"
                         "   96:  `  a  b  c  d  e  f  g  h  i  j  k  l  m  n  o\n"
                         "  112:  p  q  r  s  t  u  v  w  x  y  z  {  |  }  ~ 7f\n"
                         "  128: 80 81 82 83 84 85 86 87 88 89 8a 8b 8c 8d 8e 8f\n"
                         "  144: 90 91 92 93 94 95 96 97 98 99 9a 9b 9c 9d 9e 9f\n"
                         "  160: a0 a1 a2 a3 a4 a5 a6 a7 a8 a9 aa ab ac ad ae af\n"
                         "  176: b0 b1 b2 b3 b4 b5 b6 b7 b8 b9 ba bb bc bd be bf\n"
                         "  192: c0 c1 c2 c3 c4 c5 c6 c7 c8 c9 ca cb cc cd ce cf\n"
                         "  208: d0 d1 d2 d3 d4 d5 d6 d7 d8 d9 da db dc dd de df\n"
                         "  224: e0 e1 e2 e3 e4 e5 e6 e7 e8 e9 ea eb ec ed ee ef\n"
                         "  240: f0 f1 f2 f3 f4 f5 f6 f7 f8 f9 fa fb fc fd fe ff");
    EXPECT_EQ(expected, ost.str());

    ost.str("");
    ost << "\n";
    StringUtil::printAsHex(ost, &asciitable[0], asciitable.size(),
                           15, false);
    expected = "\n"
               "  0: 00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e ...............\n"
               " 15: 0f 10 11 12 13 14 15 16 17 18 19 1a 1b 1c 1d ...............\n"
               " 30: 1e 1f 20 21 22 23 24 25 26 27 28 29 2a 2b 2c ...!\"#$%&'()*+,\n"
               " 45: 2d 2e 2f 30 31 32 33 34 35 36 37 38 39 3a 3b -./0123456789:;\n"
               " 60: 3c 3d 3e 3f 40 41 42 43 44 45 46 47 48 49 4a <=>?@ABCDEFGHIJ\n"
               " 75: 4b 4c 4d 4e 4f 50 51 52 53 54 55 56 57 58 59 KLMNOPQRSTUVWXY\n"
               " 90: 5a 5b 5c 5d 5e 5f 60 61 62 63 64 65 66 67 68 Z[\\]^_`abcdefgh\n"
               "105: 69 6a 6b 6c 6d 6e 6f 70 71 72 73 74 75 76 77 ijklmnopqrstuvw\n"
               "120: 78 79 7a 7b 7c 7d 7e 7f 80 81 82 83 84 85 86 xyz{|}~........\n"
               "135: 87 88 89 8a 8b 8c 8d 8e 8f 90 91 92 93 94 95 ...............\n"
               "150: 96 97 98 99 9a 9b 9c 9d 9e 9f a0 a1 a2 a3 a4 ...............\n"
               "165: a5 a6 a7 a8 a9 aa ab ac ad ae af b0 b1 b2 b3 ...............\n"
               "180: b4 b5 b6 b7 b8 b9 ba bb bc bd be bf c0 c1 c2 ...............\n"
               "195: c3 c4 c5 c6 c7 c8 c9 ca cb cc cd ce cf d0 d1 ...............\n"
               "210: d2 d3 d4 d5 d6 d7 d8 d9 da db dc dd de df e0 ...............\n"
               "225: e1 e2 e3 e4 e5 e6 e7 e8 e9 ea eb ec ed ee ef ...............\n"
               "240: f0 f1 f2 f3 f4 f5 f6 f7 f8 f9 fa fb fc fd fe ...............\n"
               "255: ff                                           .";
    EXPECT_EQ(expected, ost.str());
}
