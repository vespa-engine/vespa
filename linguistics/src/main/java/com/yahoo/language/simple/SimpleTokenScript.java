package com.yahoo.language.simple;

import com.yahoo.language.process.TokenScript;

/**
 * @author mariusarhaug
 */

class SimpleTokenScript {

    static TokenScript valueOf(int codePoint) {
        return switch(Character.UnicodeScript.of(codePoint))
        {
            case COMMON -> TokenScript.COMMON;
            case LATIN -> TokenScript.LATIN;
            case GREEK -> TokenScript.GREEK;
            case CYRILLIC -> TokenScript.CYRILLIC;
            case ARMENIAN -> TokenScript.ARMENIAN;
            case HEBREW -> TokenScript.HEBREW;
            case ARABIC -> TokenScript.ARABIC;
            case SYRIAC -> TokenScript.SYRIAC;
            case THAANA -> TokenScript.THAANA;
            case DEVANAGARI -> TokenScript.DEVANAGARI;
            case GURMUKHI -> TokenScript.GURMUKHI;
            case GUJARATI -> TokenScript.GUJARATI;
            case ORIYA -> TokenScript.ORIYA;
            case TAMIL -> TokenScript.TAMIL;
            case TELUGU -> TokenScript.TELUGU;
            case KANNADA -> TokenScript.KANNADA;
            case MALAYALAM -> TokenScript.MALAYALAM;
            case SINHALA -> TokenScript.SINHALA;
            case THAI -> TokenScript.THAI;
            case LAO -> TokenScript.LAO;
            case TIBETAN -> TokenScript.TIBETAN;
            case MYANMAR -> TokenScript.MYANMAR;
            case GEORGIAN -> TokenScript.GEORGIAN;
            case HANGUL -> TokenScript.HANGUL;
            case ETHIOPIC -> TokenScript.ETHIOPIC;
            case CHEROKEE -> TokenScript.CHEROKEE;
            case OGHAM -> TokenScript.OGHAM;
            case RUNIC -> TokenScript.RUNIC;
            case KHMER -> TokenScript.KHMER;
            case MONGOLIAN -> TokenScript.MONGOLIAN;
            case HIRAGANA -> TokenScript.HIRAGANA;
            case KATAKANA -> TokenScript.KATAKANA;
            case HAN -> TokenScript.HAN;
            case YI -> TokenScript.YI;
            case GOTHIC -> TokenScript.GOTHIC;
            case DESERET -> TokenScript.DESERET;
            case INHERITED -> TokenScript.INHERITED;
            case TAGALOG -> TokenScript.TAGALOG;
            case HANUNOO -> TokenScript.HANUNOO;
            case BUHID -> TokenScript.BUHID;
            case TAGBANWA -> TokenScript.TAGBANWA;
            case LIMBU -> TokenScript.LIMBU;
            case UGARITIC -> TokenScript.UGARITIC;
            case SHAVIAN -> TokenScript.SHAVIAN;
            case OSMANYA -> TokenScript.OSMANYA;
            case CYPRIOT -> TokenScript.CYPRIOT;
            case BRAILLE -> TokenScript.BRAILLE;
            case BUGINESE -> TokenScript.BUGINESE;
            case COPTIC -> TokenScript.COPTIC;
            case GLAGOLITIC -> TokenScript.GLAGOLITIC;
            case KHAROSHTHI -> TokenScript.KHAROSHTHI;
            case TIFINAGH -> TokenScript.TIFINAGH;

            default -> TokenScript.UNKNOWN;
        };
    }
}

