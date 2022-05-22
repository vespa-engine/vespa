// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "normwordfolder.h"
#include <mutex>
#include <cstring>

bool Fast_NormalizeWordFolder::_isInitialized = false;
std::mutex _initMutex;
bool Fast_NormalizeWordFolder::_doAccentRemoval = false;
bool Fast_NormalizeWordFolder::_doSharpSSubstitution = false;
bool Fast_NormalizeWordFolder::_doLigatureSubstitution = false;
bool Fast_NormalizeWordFolder::_doMulticharExpansion = false;
bool Fast_NormalizeWordFolder::_isWord[128];

ucs4_t Fast_NormalizeWordFolder::_foldCase[767]; // Up to Latin Extended B (0x0250)
ucs4_t Fast_NormalizeWordFolder::_foldCaseHighAscii[256]; // Latin Extended Additional (0x1E00 - 0x1F00)
ucs4_t Fast_NormalizeWordFolder::_kanaMap[192];
ucs4_t Fast_NormalizeWordFolder::_halfwidth_fullwidthMap[240];

void
Fast_NormalizeWordFolder::Setup(uint32_t flags)
{
    // Only allow setting these when not initialized or initializing...
    {
        std::lock_guard<std::mutex> initGuard(_initMutex);
        _doAccentRemoval         = (DO_ACCENT_REMOVAL           & flags) != 0;
        _doSharpSSubstitution    = (DO_SHARP_S_SUBSTITUTION     & flags) != 0;
        _doLigatureSubstitution  = (DO_LIGATURE_SUBSTITUTION    & flags) != 0;
        _doMulticharExpansion    = (DO_MULTICHAR_EXPANSION      & flags) != 0;
        _isInitialized = false;
    }
    Initialize();
}

void
Fast_NormalizeWordFolder::Initialize()
{
    unsigned int i;
    if (!_isInitialized) {
        std::lock_guard<std::mutex> initGuard(_initMutex);
        if (!_isInitialized) {

            for (i = 0; i < 128; i++)
                _isWord[i] = Fast_UnicodeUtil::IsWordChar(i);
            for (i = 0; i < 767; i++) {
                _foldCase[i] = Fast_UnicodeUtil::ToLower(i);
            }

            for (i = 0x1E00; i < 0x1F00; i++) {
                _foldCaseHighAscii[i - 0x1E00] = Fast_UnicodeUtil::ToLower(i);
            }

            if (_doAccentRemoval) {
                _foldCase[0xc0] = 'a';
                _foldCase[0xc1] = 'a';
                _foldCase[0xc2] = 'a';
                _foldCase[0xc3] = 'a';  // A tilde
                _foldCase[0xc7] = 'c';
                _foldCase[0xc8] = 'e';
                _foldCase[0xc9] = 'e';
                _foldCase[0xca] = 'e';
                _foldCase[0xcb] = 'e';
                _foldCase[0xcc] = 'i';  // I grave
                _foldCase[0xcd] = 'i';
                _foldCase[0xce] = 'i';
                _foldCase[0xcf] = 'i';
                _foldCase[0xd1] = 'n';
                _foldCase[0xd2] = 'o';
                _foldCase[0xd3] = 'o';
                _foldCase[0xd4] = 'o';
                _foldCase[0xd5] = 'o';
                _foldCase[0xd9] = 'u';
                _foldCase[0xda] = 'u';
                _foldCase[0xdb] = 'u';
                _foldCase[0xdd] = 'y';

                _foldCase[0xe0] = 'a';
                _foldCase[0xe1] = 'a';
                _foldCase[0xe2] = 'a';
                _foldCase[0xe3] = 'a'; // a tilde
                _foldCase[0xe7] = 'c';
                _foldCase[0xe8] = 'e';
                _foldCase[0xe9] = 'e';
                _foldCase[0xea] = 'e';
                _foldCase[0xeb] = 'e';
                _foldCase[0xec] = 'i'; // i grave
                _foldCase[0xed] = 'i';
                _foldCase[0xee] = 'i';
                _foldCase[0xef] = 'i';
                _foldCase[0xf1] = 'n';
                _foldCase[0xf2] = 'o';
                _foldCase[0xf3] = 'o';
                _foldCase[0xf4] = 'o';
                _foldCase[0xf5] = 'o';
                _foldCase[0xf9] = 'u';
                _foldCase[0xfa] = 'u';
                _foldCase[0xfb] = 'u';
                _foldCase[0xfd] = 'y';
                _foldCase[0xff] = 'y';
                _foldCase[0x102] = 'a';
                _foldCase[0x103] = 'a';
                _foldCase[0x110] = 'd';
                _foldCase[0x111] = 'd';
                _foldCase[0x128] = 'i';
                _foldCase[0x129] = 'i';
                _foldCase[0x178] = 'y';
                _foldCase[0x1a0] = 'o';
                _foldCase[0x1a1] = 'o';
                _foldCase[0x1af] = 'u';
                _foldCase[0x1b0] = 'u';

                // Superscript spacing modifiers
                _foldCase[0x2b0] = 'h';
                _foldCase[0x2b1] = 0x266;
                _foldCase[0x2b2] = 'j';
                _foldCase[0x2b3] = 'r';
                _foldCase[0x2b4] = 0x279;
                _foldCase[0x2b5] = 0x27b;
                _foldCase[0x2b6] = 0x281;
                _foldCase[0x2b7] = 'w';
                _foldCase[0x2b8] = 'y';
                _foldCase[0x2e0] = 0x263;
                _foldCase[0x2e1] = 'l';
                _foldCase[0x2e2] = 's';
                _foldCase[0x2e3] = 'x';
                _foldCase[0x2e4] = 0x295;

                // Superscript spacing modifiers
                _foldCase[0x2b0] = 'h';
                _foldCase[0x2b1] = 0x266;
                _foldCase[0x2b2] = 'j';
                _foldCase[0x2b3] = 'r';
                _foldCase[0x2b4] = 0x279;
                _foldCase[0x2b5] = 0x27b;
                _foldCase[0x2b6] = 0x281;
                _foldCase[0x2b7] = 'w';
                _foldCase[0x2b8] = 'y';
                _foldCase[0x2e0] = 0x263;
                _foldCase[0x2e1] = 'l';
                _foldCase[0x2e2] = 's';
                _foldCase[0x2e3] = 'x';
                _foldCase[0x2e4] = 0x295;

                // Deaccenting-table for Ascii Extended Additional
                _foldCaseHighAscii[0x1ea0 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea1 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea2 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea3 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea4 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea5 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea6 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea7 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea8 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ea9 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eaa - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eab - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eac - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1ead - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eae - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eaf - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb0 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb1 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb2 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb3 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb4 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb5 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb6 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb7 - 0x1e00] = 'a';
                _foldCaseHighAscii[0x1eb8 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1eb9 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1eba - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ebb - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ebc - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ebd - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ebe - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ebf - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec0 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec1 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec2 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec3 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec4 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec5 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec6 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec7 - 0x1e00] = 'e';
                _foldCaseHighAscii[0x1ec8 - 0x1e00] = 'i';
                _foldCaseHighAscii[0x1ec9 - 0x1e00] = 'i';
                _foldCaseHighAscii[0x1eca - 0x1e00] = 'i';
                _foldCaseHighAscii[0x1ecb - 0x1e00] = 'i';
                _foldCaseHighAscii[0x1ecc - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ecd - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ece - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ecf - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed0 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed1 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed2 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed3 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed4 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed5 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed6 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed7 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed8 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ed9 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1eda - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1edb - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1edc - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1edd - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ede - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1edf - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ee0 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ee1 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ee2 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ee3 - 0x1e00] = 'o';
                _foldCaseHighAscii[0x1ee4 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ee5 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ee6 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ee7 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ee8 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ee9 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1eea - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1eeb - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1eec - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1eed - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1eee - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1eef - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ef0 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ef1 - 0x1e00] = 'u';
                _foldCaseHighAscii[0x1ef2 - 0x1e00] = 'y';
                _foldCaseHighAscii[0x1ef3 - 0x1e00] = 'y';
                _foldCaseHighAscii[0x1ef4 - 0x1e00] = 'y';
                _foldCaseHighAscii[0x1ef5 - 0x1e00] = 'y';
                _foldCaseHighAscii[0x1ef6 - 0x1e00] = 'y';
                _foldCaseHighAscii[0x1ef7 - 0x1e00] = 'y';
                _foldCaseHighAscii[0x1ef8 - 0x1e00] = 'y';
                _foldCaseHighAscii[0x1ef9 - 0x1e00] = 'y';
            }

            // Base case hiragana - hiragana ID
            for (i = 0; i < 96; i++) {
                _kanaMap[i] = 0x3040 + i;
            }

            // katakana - katakana ID
            for (i = 96; i < 192; i++) {
                _kanaMap[i] = 0x3040 + i;
            }

            // Fullwidth ASCII
            for (i = 0; i < 0x21; i++)
                _halfwidth_fullwidthMap[i] = 0x20 + i;
            for (i = 0x21; i < 0x3B; i++) // full uppercase to half lowercase
                _halfwidth_fullwidthMap[i] = 0x40 + i;
            for (i = 0x3B; i < 0x5F; i++)
                _halfwidth_fullwidthMap[i] = 0x20 + i;
            // 0xFF00, 0xFF5F -> id
            _halfwidth_fullwidthMap[0x00] = 0xFF00;
            _halfwidth_fullwidthMap[0x5F] = 0xFF5F;

            // Halfwidth CJK Punctuation
            // 0xFF60 -> id
            _halfwidth_fullwidthMap[0x60] = 0xFF60;
            _halfwidth_fullwidthMap[0x61] = 0x3002;
            _halfwidth_fullwidthMap[0x62] = 0x300C;
            _halfwidth_fullwidthMap[0x63] = 0x300D;
            _halfwidth_fullwidthMap[0x64] = 0x3001;

            // Halfwidth katakana (maps directly to hiragana)

            // Common cases for halfwidth katakana
            _halfwidth_fullwidthMap[0x65] = 0x30FB;

            _halfwidth_fullwidthMap[0x66] = 0x30F2;
            _halfwidth_fullwidthMap[0x6F] = 0x30C3;
            _halfwidth_fullwidthMap[0x70] = 0x30FC;
            _halfwidth_fullwidthMap[0x71] = 0x30A2;
            _halfwidth_fullwidthMap[0x72] = 0x30A4;
            _halfwidth_fullwidthMap[0x73] = 0x30A6;
            _halfwidth_fullwidthMap[0x74] = 0x30A8;
            _halfwidth_fullwidthMap[0x75] = 0x30AA;
            _halfwidth_fullwidthMap[0x76] = 0x30AB;
            _halfwidth_fullwidthMap[0x77] = 0x30AD;
            _halfwidth_fullwidthMap[0x78] = 0x30AF;
            _halfwidth_fullwidthMap[0x79] = 0x30B1;
            _halfwidth_fullwidthMap[0x7A] = 0x30B3;
            _halfwidth_fullwidthMap[0x7B] = 0x30B5;
            _halfwidth_fullwidthMap[0x7C] = 0x30B7;
            _halfwidth_fullwidthMap[0x7D] = 0x30B9;
            _halfwidth_fullwidthMap[0x7E] = 0x30BB;
            _halfwidth_fullwidthMap[0x7F] = 0x30BD;
            _halfwidth_fullwidthMap[0x80] = 0x30BF;
            _halfwidth_fullwidthMap[0x81] = 0x30C1;
            _halfwidth_fullwidthMap[0x82] = 0x30C4;
            _halfwidth_fullwidthMap[0x83] = 0x30C6;
            _halfwidth_fullwidthMap[0x84] = 0x30C8;
            _halfwidth_fullwidthMap[0x85] = 0x30CA;
            _halfwidth_fullwidthMap[0x86] = 0x30CB;
            _halfwidth_fullwidthMap[0x87] = 0x30CC;
            _halfwidth_fullwidthMap[0x88] = 0x30CD;
            _halfwidth_fullwidthMap[0x89] = 0x30CE;
            _halfwidth_fullwidthMap[0x8A] = 0x30CF;
            _halfwidth_fullwidthMap[0x8B] = 0x30D2;
            _halfwidth_fullwidthMap[0x8C] = 0x30D5;
            _halfwidth_fullwidthMap[0x8D] = 0x30D8;
            _halfwidth_fullwidthMap[0x8E] = 0x30DB;
            _halfwidth_fullwidthMap[0x8F] = 0x30DE;
            _halfwidth_fullwidthMap[0x90] = 0x30DF;
            _halfwidth_fullwidthMap[0x91] = 0x30E0;
            _halfwidth_fullwidthMap[0x92] = 0x30E1;
            _halfwidth_fullwidthMap[0x93] = 0x30E2;
            _halfwidth_fullwidthMap[0x94] = 0x30E4;
            _halfwidth_fullwidthMap[0x95] = 0x30E6;
            _halfwidth_fullwidthMap[0x96] = 0x30E8;
            _halfwidth_fullwidthMap[0x97] = 0x30E9;
            _halfwidth_fullwidthMap[0x98] = 0x30EA;
            _halfwidth_fullwidthMap[0x99] = 0x30EB;
            _halfwidth_fullwidthMap[0x9A] = 0x30EC;
            _halfwidth_fullwidthMap[0x9B] = 0x30ED;
            _halfwidth_fullwidthMap[0x9C] = 0x30EF;
            _halfwidth_fullwidthMap[0x9D] = 0x30F3;
            _halfwidth_fullwidthMap[0x9E] = 0x3099;
            _halfwidth_fullwidthMap[0x9F] = 0x309A;

            _halfwidth_fullwidthMap[0x67] = 0x30a1;
            _halfwidth_fullwidthMap[0x68] = 0x30a3;
            _halfwidth_fullwidthMap[0x69] = 0x30a5;
            _halfwidth_fullwidthMap[0x6A] = 0x30a7;
            _halfwidth_fullwidthMap[0x6B] = 0x30a9;
            _halfwidth_fullwidthMap[0x6C] = 0x30e3;
            _halfwidth_fullwidthMap[0x6D] = 0x30e5;
            _halfwidth_fullwidthMap[0x6E] = 0x30e7;

            // Halfwidth Hangul
            _halfwidth_fullwidthMap[0xA0] = 0x3164;
            // fill in 0xFFA1 - 0xFFBE => 0x3131 - 0x314E
            for (i = 0xA1; i < 0xBF; i++)
                _halfwidth_fullwidthMap[i] = 0x3090 + i;
            _halfwidth_fullwidthMap[0xBF] = 0xFFBF;
            _halfwidth_fullwidthMap[0xC0] = 0xFFC0;
            _halfwidth_fullwidthMap[0xC1] = 0xFFC1;
            // fill in 0xFFC2 - 0xFFC7 => 0x314F - 0x3154
            for (i = 0xC2; i < 0xC8; i++)
                _halfwidth_fullwidthMap[i] = 0x308D + i;
            _halfwidth_fullwidthMap[0xC8] = 0xFFC8;
            _halfwidth_fullwidthMap[0xC9] = 0xFFC9;
            // fill in 0xFFCA - 0xFFCF => 0x3155 - 0x315A
            for (i = 0xCA; i < 0xD0; i++)
                _halfwidth_fullwidthMap[i] = 0x308B + i;
            _halfwidth_fullwidthMap[0xD0] = 0xFFD0;
            _halfwidth_fullwidthMap[0xD1] = 0xFFD1;
            // fill in 0xFFD2 - 0xFFD7 => 0x315B - 0x3160
            for (i = 0xD2; i < 0xD8; i++)
                _halfwidth_fullwidthMap[i] = 0x3089 + i;
            _halfwidth_fullwidthMap[0xD8] = 0xFFD8;
            _halfwidth_fullwidthMap[0xD9] = 0xFFD9;
            // fill in 0xFFDA - 0xFFDC => 0x3161 - 0x3163
            for (i = 0xDA; i < 0xDD; i++)
                _halfwidth_fullwidthMap[i] = 0x3087 + i;

            // Fullwidth symbols
            _halfwidth_fullwidthMap[0xE0] = 0x00A2;
            _halfwidth_fullwidthMap[0xE1] = 0x00A3;
            _halfwidth_fullwidthMap[0xE2] = 0x00AC;
            _halfwidth_fullwidthMap[0xE3] = 0x00AF;
            _halfwidth_fullwidthMap[0xE4] = 0x00A6;
            _halfwidth_fullwidthMap[0xE5] = 0x00A5;
            _halfwidth_fullwidthMap[0xE6] = 0x20A9;

            // 0xFFE7 -> id
            _halfwidth_fullwidthMap[0xE7] = 0xFFE7;

            // Halfwidth symbols
            _halfwidth_fullwidthMap[0xE8] = 0x2502;
            _halfwidth_fullwidthMap[0xE9] = 0x2190;
            _halfwidth_fullwidthMap[0xEA] = 0x2191;
            _halfwidth_fullwidthMap[0xEB] = 0x2192;
            _halfwidth_fullwidthMap[0xEC] = 0x2193;
            _halfwidth_fullwidthMap[0xED] = 0x25A0;
            _halfwidth_fullwidthMap[0xEE] = 0x25CB;

            // 0xFFEF -> id
            _halfwidth_fullwidthMap[0xEF] = 0xFFEF;


            //
            // DONE
            //
            _isInitialized = true;
        }
    }
}

Fast_NormalizeWordFolder::Fast_NormalizeWordFolder()
{
    Initialize();
}


Fast_NormalizeWordFolder::~Fast_NormalizeWordFolder(void)
{
}

const char*
Fast_NormalizeWordFolder::UCS4Tokenize(const char *buf,
                                   const char *bufend,
                                   ucs4_t *dstbuf,
                                   ucs4_t *dstbufend,
                                   const char*& origstart,
                                   size_t& tokenlen) const
{

    ucs4_t c;
    const unsigned char *p, *ep;
    ucs4_t *q, *eq;
    p = reinterpret_cast<const unsigned char *>(buf);
    ep = reinterpret_cast<const unsigned char *>(bufend);

    // Skip characters between words
    for (;;) {
        if (p >= ep) {		// End of input buffer, no more words
            *dstbuf = 0;
            return reinterpret_cast<const char *>(p);
        }
        if (*p < 128) {		// Common case, ASCII
            c = *p++;
            if (_isWord[c])
            {
                origstart = reinterpret_cast<const char *>(p) - 1;
                break;
            }
        } else {
            const unsigned char* prev_p = p;
            c = Fast_UnicodeUtil::GetUTF8Char(p);
            if (IsWordCharOrIA(c))
            {
                origstart = reinterpret_cast<const char *>(prev_p);
                break;
            }
        }
    }

    // Start saving word.
    q = dstbuf;
    eq = dstbufend - 3;		// Make room for UCS4 char replacement string and NUL
    // Doesn't check for space for the first char, assumes that
    // word buffer is at least 13 characters
    if (c < 128) {		// Common case, ASCII
        *q++ = _foldCase[c];
    } else {
        const char *repl = ReplacementString(c);
        if (repl != nullptr) {
            size_t repllen = strlen(repl);
            if (repllen > 0)
                q = Fast_UnicodeUtil::ucs4copy(q,repl);
        } else {
            c = ToFold(c);
            *q++ = c;
        }
    }

    // Special case for interlinear annotation
    if (c == 0xFFF9) { // ANCHOR
        // Collect up to and including terminator
        for(;;) {
            if (p >= ep) {
                c = 0;
                break;
            }
            if (*p < 128) {  // Note, no exit on plain ASCII
                c = *p++;
                *q++ = c;
                if (q >= eq) { // Junk rest of annotation block
                    for (;;) {
                        if (p >= ep) {	// End of input buffer
                            c = 0;
                            break;
                        }
                        if (*p < 128) {	// Common case, ASCII
                            c = *p++;
                        } else {
                            c = Fast_UnicodeUtil::GetUTF8Char(p);
                            if (c == 0xFFFB) {
                                break; // out of junking loop
                            }
                        }
                    }
                    break; // out of annotation block processing
                }
            } else {
                c = Fast_UnicodeUtil::GetUTF8Char(p);
                *q++ = c;
                if (c == 0xFFFB) { // TERMINATOR => Exit condition
                    break;
                }
                if (q >= eq) {		// Junk rest of word
                    for (;;) {
                        if (p >= ep) {	// End of input buffer
                            c = 0;
                            break;
                        }
                        if (*p < 128) {	// Common case, ASCII
                            c = *p++;
                        } else {
                            c = Fast_UnicodeUtil::GetUTF8Char(p);
                            if (c == 0xFFFB) {
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
    } else

        for (;;) {
            if (p >= ep) {		// End of input buffer
                c = 0;
                break;
            }
            if (*p < 128) {		// Common case, ASCII
                c = *p++;
                if (!_isWord[c])
                {
                    p--;
                    break;
                }
                *q++ = _foldCase[c];
                if (q >= eq) {		// Junk rest of word
                    for (;;) {
                        if (p >= ep) {	// End of input buffer
                            c = 0;
                            break;
                        }
                        if (*p < 128) {	// Common case, ASCII
                            c = *p++;
                            if (!_isWord[c])
                            {
                                p--;
                                break;
                            }
                        } else {
                            const unsigned char* prev_p = p;
                            c = Fast_UnicodeUtil::GetUTF8Char(p);
                            if (!Fast_UnicodeUtil::IsWordChar(c))
                            {
                                p = prev_p;
                                break;
                            }
                        }
                    }
                    break;
                }
            } else {
                const unsigned char* prev_p = p;
                c = Fast_UnicodeUtil::GetUTF8Char(p);
                if (!Fast_UnicodeUtil::IsWordChar(c))
                {
                    p = prev_p;
                    break;
                }
                const char *repl = ReplacementString(c);
                if (repl != nullptr) {
                    size_t repllen = strlen(repl);
                    if (repllen > 0)
                        q = Fast_UnicodeUtil::ucs4copy(q,repl);
                } else {
                    c = ToFold(c);
                    *q++ = c;
                }
                if (q >= eq) {		// Junk rest of word
                    for (;;) {
                        if (p >= ep) {	// End of input buffer
                            c = 0;
                            break;
                        }
                        if (*p < 128) {	// Common case, ASCII
                            c = *p++;
                            if (!_isWord[c])
                            {
                                p--;
                                break;
                            }
                        } else {
                            const unsigned char* xprev_p = p;
                            c = Fast_UnicodeUtil::GetUTF8Char(p);
                            if (!Fast_UnicodeUtil::IsWordChar(c))
                            {
                                p = xprev_p;
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
    *q = 0;
    tokenlen = q - dstbuf;
    return reinterpret_cast<const char *>(p);
}
