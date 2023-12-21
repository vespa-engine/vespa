// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "wordfolder.h"
#include <cstdint>

/**
 * WordFolder that both lowercases, removes accents, and converts
 * katakana to hiragana.
 */
class Fast_NormalizeWordFolder : public Fast_WordFolder
{
public:
    enum {
        DO_ACCENT_REMOVAL =           0x1 << 0,
        DO_SMALL_TO_NORMAL_KANA =     0x1 << 1,
        DO_KATAKANA_TO_HIRAGANA =     0x1 << 2,
        DO_KANA_ACCENT_COLLAPSING =   0x1 << 3, // Code not implemented
        DO_FULLWIDTH_TO_BASIC_LATIN = 0x1 << 4, // Code not implemented
        DO_SHARP_S_SUBSTITUTION =     0x1 << 5,
        DO_LIGATURE_SUBSTITUTION =    0x1 << 6,
        DO_MULTICHAR_EXPANSION =      0x1 << 7
    };
    Fast_NormalizeWordFolder();
    ~Fast_NormalizeWordFolder() override;
    const char* UCS4Tokenize(const char *buf, const char *bufend, ucs4_t *dstbuf,
                             ucs4_t *dstbufend, const char*& origstart, size_t& tokenlen) const override;
    /**
     * Setup behaviour prior to constructing an object.
     * Not needed if default behaviour is wanted. The default is
     * DO_ACCENT_REMOVAL + DO_SHARP_S_SUBSTITUTION + DO_LIGATURE_SUBSTITUTION.
     *
     * @param flags The flags should be taken from the DO_ constants,
     *              added together.
     */
    static void Setup(uint32_t flags);
    static ucs4_t lowercase_and_fold_ascii(ucs4_t c) noexcept { return _lowerCase[c]; }
    static ucs4_t lowercase_ascii(ucs4_t c) noexcept { return _foldCase[c]; }
    static bool is_wordchar_ascii7bit(ucs4_t c) noexcept { return _isWord[c]; }
    static ucs4_t lowercase(ucs4_t c) {
        if (c < 767)
            return _lowerCase[c];
        else if (c >= 0x1E00 && c < 0x1F00)
            return _lowerCaseHighAscii[c - 0x1E00];
        else
        if (c >= 0x3040 && c < 0x3100)
            return _kanaMap[c - 0x3040];
        else
            if (c >= 0xFF00 && c < 0xFFF0)
                return _halfwidth_fullwidthMap[c - 0xFF00];
            else
                return Fast_UnicodeUtil::ToLower(c);
    }
    static ucs4_t lowercase_and_fold(ucs4_t c) {
        if (c < 767)
            return _foldCase[c];
        else if (c >= 0x1E00 && c < 0x1F00)
            return _foldCaseHighAscii[c - 0x1E00];
        else
            if (c >= 0x3040 && c < 0x3100)
                return _kanaMap[c - 0x3040];
            else
                if (c >= 0xFF00 && c < 0xFFF0)
                    return _halfwidth_fullwidthMap[c - 0xFF00];
                else
                    return Fast_UnicodeUtil::ToLower(c);
    }

    static const char *ReplacementString(ucs4_t testchar) {
        if (testchar < 0xc4 || testchar > 0x1f3) {
            return nullptr;
        }
        if (testchar == 0xdf && _doSharpSSubstitution) {
            return "ss";
        }
        if (_doLigatureSubstitution) {
            switch (testchar) {
            case 0x132:
            case 0x133:
                return "ij";
            case 0x13f:
            case 0x140:
                return "l";  // Latin L with middlepoint
            case 0x149:
                return "n";  // Latin small n preceded by apostrophe
            case 0x17f:
                return "s";  // Latin small letter long s
            case 0x1c7:
            case 0x1c8:
            case 0x1c9:
                return "lj";
            case 0x1ca:
            case 0x1cb:
            case 0x1cc:
                return "nj";
            case 0x1f1:
            case 0x1f2:
            case 0x1f3:
                return "dz";
            }
        }
        if (_doMulticharExpansion) {
            switch(testchar) {
            case 0xc4:
            case 0xe4: // A/a with diaeresis
                return "ae";

            case 0xc5:
            case 0xe5: // A/a with ring
                return "aa";

            case 0xc6:
            case 0xe6: // Letter/ligature AE/ae
                return "ae";

            case 0xd6:
            case 0xf6: // O/o with diaeresis
                return "oe";

            case 0xd8:
            case 0xf8: // O/o with stroke
                return "oe";

            case 0xdc:
            case 0xfc: // U/u with diaeresis
                return "ue";

            case 0xd0:
            case 0xf0: // norse "eth"
                return "d";

            case 0xde:
            case 0xfe: // norse "thorn"
                return "th";

            default:
                return nullptr;

            }

        }
        return nullptr;
    }
private:
    /**
     * Freeze the config, either from call to Setup, environment
     * or defaults.
     */
    static void Initialize();
    static bool IsWordCharOrIA(ucs4_t c) {
        return Fast_UnicodeUtil::IsWordChar(c) || c == 0xFFF9 || c == 0xFFFA || c == 0xFFFB;
    }

    /** character tables */
    static bool _isWord[128];
    static ucs4_t _foldCase[767]; // Up to Spacing Modifiers, inclusize (0x02FF)
    static ucs4_t _lowerCase[767];
    static ucs4_t _foldCaseHighAscii[256]; // Latin Extended Additional (0x1E00 - 0x1F00) (incl. vietnamese)
    static ucs4_t _lowerCaseHighAscii[256];
    /** Map the values from range 0x3040 (0) - 0x30FF (191). */
    static ucs4_t _kanaMap[192];
    static ucs4_t _halfwidth_fullwidthMap[240];
    static bool   _isInitialized;
    static bool   _doAccentRemoval;
    static bool   _doSharpSSubstitution;
    static bool   _doLigatureSubstitution;
    static bool   _doMulticharExpansion;
};
