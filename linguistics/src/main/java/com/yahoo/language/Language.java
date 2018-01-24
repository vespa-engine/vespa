// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language;

import com.yahoo.text.Lowercase;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author Rich Pito
 */
public enum Language {

    /** Language tag "un". */
    UNKNOWN("un"),

    /** Language tag "ab". */
    ABKHAZIAN("ab"),

    /** Language tag "aa". */
    AFAR("aa"),

    /** Language tag "af". */
    AFRIKAANS("af"),

    /** Language tag "sq". */
    ALBANIAN("sq"),

    /** Language tag "am". */
    AMHARIC("am"),

    /** Language tag "ar". */
    ARABIC("ar"),

    /** Language tag "hy". */
    ARMENIAN("hy"),

    /** Language tag "as". */
    ASSAMESE("as"),

    /** Language tag "ay". */
    AYMARA("ay"),

    /** Language tag "az". */
    AZERBAIJANI("az"),

    /** Language tag "ba". */
    BASHKIR("ba"),

    /** Language tag "eu". */
    BASQUE("eu"),

    /** Language tag "bn". */
    BENGALI("bn"),

    /** Language tag "dz". */
    BHUTANI("dz"),

    /** Language tag "bh". */
    BIHARI("bh"),

    /** Language tag "bi". */
    BISLAMA("bi"),

    /** Language tag "br". */
    BRETON("br"),

    /** Language tag "bug". */
    BUGINESE("bug"),

    /** Language tag "bg". */
    BULGARIAN("bg"),

    /** Language tag "my". */
    BURMESE("my"),

    /** Language tag "be". */
    BYELORUSSIAN("be"),

    /** Language tag "km". */
    CAMBODIAN("km"),

    /** Language tag "ca". */
    CATALAN("ca"),

    /** Language tag "chr". */
    CHEROKEE("chr"),

    /**
     * Language tag "zh-hans".
     *
     * @see #fromLocale(Locale)
     */
    CHINESE_SIMPLIFIED("zh-hans"),

    /**
     * Language tag "zh-hant".
     *
     * @see #fromLocale(Locale)
     */
    CHINESE_TRADITIONAL("zh-hant"),

    /** Language tag "cop". */
    COPTIC("cop"),

    /** Language tag "co". */
    CORSICAN("co"),

    /** Language tag "hr". */
    CROATIAN("hr"),

    /** Language tag "cs". */
    CZECH("cs"),

    /** Language tag "da". */
    DANISH("da"),

    /** Language tag "div". */
    DIVEHI("div"),

    /** Language tag "nl". */
    DUTCH("nl"),

    /** Language tag "en". */
    ENGLISH("en"),

    /** Language tag "eo". */
    ESPERANTO("eo"),

    /** Language tag "et". */
    ESTONIAN("et"),

    /** Language tag "fo". */
    FAROESE("fo"),

    /** Language tag "fj". */
    FIJI("fj"),

    /** Language tag "fi". */
    FINNISH("fi"),

    /** Language tag "fr". */
    FRENCH("fr"),

    /** Language tag "fy". */
    FRISIAN("fy"),

    /** Language tag "gl". */
    GALICIAN("gl"),

    /** Language tag "ka". */
    GEORGIAN("ka"),

    /** Language tag "de". */
    GERMAN("de"),

    /** Language tag "got". */
    GOTHIC("got"),

    /** Language tag "el". */
    GREEK("el"),

    /** Language tag "kl". */
    GREENLANDIC("kl"),

    /** Language tag "gn". */
    GUARANI("gn"),

    /** Language tag "gu". */
    GUJARATI("gu"),

    /** Language tag "ha". */
    HAUSA("ha"),

    /**
     * Language tag "he".
     *
     * @see #fromLocale(Locale)
     */
    HEBREW("he"),

    /** Language tag "hi". */
    HINDI("hi"),

    /** Language tag "hu". */
    HUNGARIAN("hu"),

    /** Language tag "is". */
    ICELANDIC("is"),

    /**
     * Language tag "id".
     *
     * @see #fromLocale(Locale)
     */
    INDONESIAN("id"),

    /** Language tag "ia". */
    INTERLINGUA("ia"),

    /** Language tag "ie". */
    INTERLINGUE("ie"),

    /** Language tag "iu". */
    INUKTITUT("iu"),

    /** Language tag "ik". */
    INUPIAK("ik"),

    /** Language tag "ga". */
    IRISH("ga"),

    /** Language tag "it". */
    ITALIAN("it"),

    /** Language tag "ja". */
    JAPANESE("ja"),

    /** Language tag "jw". */
    JAVANESE("jw"),

    /** Language tag "kn". */
    KANNADA("kn"),

    /** Language tag "ks". */
    KASHMIRI("ks"),

    /** Language tag "kk". */
    KAZAKH("kk"),

    /** Language tag "rw". */
    KINYARWANDA("rw"),

    /** Language tag "ky". */
    KIRGHIZ("ky"),

    /** Language tag "rn". */
    KIRUNDI("rn"),

    /** Language tag "ko". */
    KOREAN("ko"),

    /** Language tag "ku". */
    KURDISH("ku"),

    /** Language tag "lo". */
    LAOTHIAN("lo"),

    /** Language tag "la". */
    LATIN("la"),

    /** Language tag "lv". */
    LATVIAN("lv"),

    /** Language tag "ln". */
    LINGALA("ln"),

    /** Language tag "lt". */
    LITHUANIAN("lt"),

    /** Language tag "mk". */
    MACEDONIAN("mk"),

    /** Language tag "mg". */
    MALAGASY("mg"),

    /** Language tag "ms". */
    MALAY("ms"),

    /** Language tag "ml". */
    MALAYALAM("ml"),

    /** Language tag "mt". */
    MALTESE("mt"),

    /** Language tag "mni". */
    MANIPURI("mni"),

    /** Language tag "mi". */
    MAORI("mi"),

    /** Language tag "mr". */
    MARATHI("mr"),

    /** Language tag "mo". */
    MOLDAVIAN("mo"),

    /** Language tag "mn". */
    MONGOLIAN("mn"),

    /** Language tag "mun". */
    MUNDA("mun"),

    /** Language tag "na". */
    NAURU("na"),

    /** Language tag "ne". */
    NEPALI("ne"),

    /**
     * Language tag "nb".
     *
     * @see #fromLocale(Locale)
     */
    NORWEGIAN_BOKMAL("nb"),

    /** Language tag "nn". */
    NORWEGIAN_NYNORSK("nn"),

    /** Language tag "oc". */
    OCCITAN("oc"),

    /** Language tag "or". */
    ORIYA("or"),

    /** Language tag "om". */
    OROMO("om"),

    /** Language tag "ps". */
    PASHTO("ps"),

    /** Language tag "fa". */
    PERSIAN("fa"),

    /** Language tag "pl". */
    POLISH("pl"),

    /** Language tag "pt". */
    PORTUGUESE("pt"),

    /** Language tag "pa". */
    PUNJABI("pa"),

    /** Language tag "qu". */
    QUECHUA("qu"),

    /** Language tag "rm". */
    RHAETO_ROMANCE("rm"),

    /** Language tag "ro". */
    ROMANIAN("ro"),

    /** Language tag "ru". */
    RUSSIAN("ru"),

    /** Language tag "sm". */
    SAMOAN("sm"),

    /** Language tag "sg". */
    SANGHO("sg"),

    /** Language tag "sa". */
    SANSKRIT("sa"),

    /** Language tag "gd". */
    SCOTS_GAELIC("gd"),

    /** Language tag "sr". */
    SERBIAN("sr"),

    /** Language tag "s". */
    SERBO_CROATIAN("sh"),

    /** Language tag "st". */
    SESOTHO("st"),

    /** Language tag "tn". */
    SETSWANA("tn"),

    /** Language tag "sn". */
    SHONA("sn"),

    /** Language tag "ii". */
    SICHUAN_YI("ii"),

    /** Language tag "sd". */
    SINDHI("sd"),

    /** Language tag "si". */
    SINHALESE("si"),

    /** Language tag "ss". */
    SISWATI("ss"),

    /** Language tag "sk". */
    SLOVAK("sk"),

    /** Language tag "sl". */
    SLOVENIAN("sl"),

    /** Language tag "so". */
    SOMALI("so"),

    /** Language tag "es". */
    SPANISH("es"),

    /** Language tag "su". */
    SUNDANESE("su"),

    /** Language tag "sw". */
    SWAHILI("sw"),

    /** Language tag "sv". */
    SWEDISH("sv"),

    /** Language tag "syr". */
    SYRIAC("syr"),

    /** Language tag "fil". */
    TAGALOG("fil"),

    /** Language tag "tg". */
    TAJIK("tg"),

    /** Language tag "ta". */
    TAMIL("ta"),

    /** Language tag "tt". */
    TATAR("tt"),

    /** Language tag "te". */
    TELUGU("te"),

    /** Language tag "th". */
    THAI("th"),

    /** Language tag "bo". */
    TIBETAN("bo"),

    /** Language tag "ti". */
    TIGRINYA("ti"),

    /** Language tag "to". */
    TONGA("to"),

    /** Language tag "ts". */
    TSONGA("ts"),

    /** Language tag "tr". */
    TURKISH("tr"),

    /** Language tag "tk". */
    TURKMEN("tk"),

    /** Language tag "tw". */
    TWI("tw"),

    /** Language tag "uga". */
    UGARITIC("uga"),

    /** Language tag "ug". */
    UIGHUR("ug"),

    /** Language tag "uk". */
    UKRAINIAN("uk"),

    /** Language tag "ur". */
    URDU("ur"),

    /** Language tag "uz". */
    UZBEK("uz"),

    /** Language tag "vi". */
    VIETNAMESE("vi"),

    /** Language tag "vo". */
    VOLAPUK("vo"),

    /** Language tag "cy". */
    WELSH("cy"),

    /** Language tag "wo". */
    WOLOF("wo"),

    /** Language tag "xh". */
    XHOSA("xh"),

    /**
     * Language tag "yi".
     *
     * @see #fromLocale(Locale)
     */
    YIDDISH("yi"),

    /** Language tag "yo". */
    YORUBA("yo"),

    /** Language tag "za". */
    ZHUANG("za"),

    /** Language tag "zu". */
    ZULU("zu");

    private static final Map<String, Language> index = new HashMap<>();
    private final String code;

    static {
        for (Language language : values()) {
            index.put(language.code, language);
        }
    }

    private Language(String code) {
        this.code = code;
    }

    public String languageCode() {
        return code;
    }

    /**
     * Returns whether this is a "cjk" language. CJK is here not a linguistic term, it is basically whether the language
     * has loose word order and a non-rigid use of space.
     *
     * @return True if this is a CJK language.
     */
    public boolean isCjk() {
        switch (this) {
            case CHINESE_SIMPLIFIED:
            case CHINESE_TRADITIONAL:
            case JAPANESE:
            case KOREAN:
            case THAI:
                return true;
            default:
                return false;
        }
    }

    /**
     * <p>Convenience method for calling <tt>fromLocale(LocaleFactory.fromLanguageTag(languageTag))</tt>.</p>
     *
     * @param languageTag The language tag for which the <tt>Language</tt> to return.
     * @return the corresponding <tt>Language</tt>, or {@link #UNKNOWN} if not known.
     */
    public static Language fromLanguageTag(String languageTag) {
        if (languageTag == null) return UNKNOWN;
        return fromLocale(LocaleFactory.fromLanguageTag(languageTag));
    }

    /**
     * <p>Returns the <tt>Language</tt> whose {@link #languageCode()} is equal to <tt>locale.getLanguage()</tt>, with
     * the following additions:</p>
     * <ul>
     * <li>Language code "in" translates to {@link #INDONESIAN}</li>
     * <li>Language code "iw" translates to {@link #HEBREW}</li>
     * <li>Language code "ji" translates to {@link #YIDDISH}</li>
     * <li>Language code "no" translates to {@link #NORWEGIAN_BOKMAL}</li>
     * <li>Language code "zh" translates to {@link #CHINESE_TRADITIONAL}, unless country code is "cn" or variant code
     * is "hans", in which case it translates to {@link #CHINESE_SIMPLIFIED}.</li>
     * </ul>
     *
     * @param locale The locale for which the <tt>Language</tt> to return.
     * @return The corresponding <tt>Language</tt>, or {@link #UNKNOWN} if not known.
     */
    public static Language fromLocale(Locale locale) {
        String str = locale.getLanguage();
        if (str.equals("in")) {
            return INDONESIAN; // Locale converts 'id' to 'in'
        }
        if (str.equals("iw")) {
            return HEBREW; // Locale converts 'he' to 'iw'
        }
        if (str.equals("ji")) {
            return YIDDISH; // Locale converts 'yi' to 'ji'
        }
        if (str.equals("no")) {
            return NORWEGIAN_BOKMAL; // alias for 'nb'
        }
        if (str.equals("zh")) {
            if (locale.getCountry().equalsIgnoreCase("cn") ||
                locale.getVariant().equalsIgnoreCase("hans")) {
                return CHINESE_SIMPLIFIED;
            }
            return CHINESE_TRADITIONAL;
        }
        Language ret = index.get(str);
        return ret != null ? ret : UNKNOWN;
    }

    /**
     * Returns the language from an encoding, or {@link #UNKNOWN} if it cannot be determined.
     *
     * @param encoding The name of the encoding to derive the <tt>Language</tt> from.
     * @return the language given by the encoding, or {@link #UNKNOWN} if not determined.
     */
    public static Language fromEncoding(String encoding) {
        if (encoding == null) return UNKNOWN;

        return fromLowerCasedEncoding(Lowercase.toLowerCase(encoding));
    }

    private static Language fromLowerCasedEncoding(String encoding) {
        if (encoding.equals("gb2312")) {
            return CHINESE_SIMPLIFIED;
        }
        if (encoding.equals("big5")) {
            return CHINESE_TRADITIONAL;
        }
        if (encoding.equals("euc-jp") ||
            encoding.equals("iso-2022-jp") ||
            encoding.equals("shift-jis")) {
            return JAPANESE;
        }
        if (encoding.equals("euc-kr")) {
            return KOREAN;
        }
        return UNKNOWN;
    }

}
