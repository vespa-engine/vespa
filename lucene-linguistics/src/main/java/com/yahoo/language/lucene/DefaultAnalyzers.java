// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.language.lucene;

import com.yahoo.language.Language;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ar.ArabicAnalyzer;
import org.apache.lucene.analysis.bg.BulgarianAnalyzer;
import org.apache.lucene.analysis.bn.BengaliAnalyzer;
import org.apache.lucene.analysis.ca.CatalanAnalyzer;
import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.ckb.SoraniAnalyzer;
import org.apache.lucene.analysis.cz.CzechAnalyzer;
import org.apache.lucene.analysis.da.DanishAnalyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.analysis.el.GreekAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.et.EstonianAnalyzer;
import org.apache.lucene.analysis.eu.BasqueAnalyzer;
import org.apache.lucene.analysis.fa.PersianAnalyzer;
import org.apache.lucene.analysis.fi.FinnishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.ga.IrishAnalyzer;
import org.apache.lucene.analysis.gl.GalicianAnalyzer;
import org.apache.lucene.analysis.hi.HindiAnalyzer;
import org.apache.lucene.analysis.hu.HungarianAnalyzer;
import org.apache.lucene.analysis.hy.ArmenianAnalyzer;
import org.apache.lucene.analysis.id.IndonesianAnalyzer;
import org.apache.lucene.analysis.it.ItalianAnalyzer;
import org.apache.lucene.analysis.lt.LithuanianAnalyzer;
import org.apache.lucene.analysis.lv.LatvianAnalyzer;
import org.apache.lucene.analysis.ne.NepaliAnalyzer;
import org.apache.lucene.analysis.nl.DutchAnalyzer;
import org.apache.lucene.analysis.no.NorwegianAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.analysis.ro.RomanianAnalyzer;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.sr.SerbianAnalyzer;
import org.apache.lucene.analysis.sv.SwedishAnalyzer;
import org.apache.lucene.analysis.ta.TamilAnalyzer;
import org.apache.lucene.analysis.te.TeluguAnalyzer;
import org.apache.lucene.analysis.th.ThaiAnalyzer;
import org.apache.lucene.analysis.tr.TurkishAnalyzer;

import java.util.Map;

import static java.util.Map.entry;

/**
 * @author dainiusjocas
 */
class DefaultAnalyzers {

    private final Map<Language, Analyzer> analyzerClasses;

    public DefaultAnalyzers() {
        analyzerClasses = Map.ofEntries(
                entry(Language.ARABIC, new ArabicAnalyzer()),
                entry(Language.BULGARIAN, new BulgarianAnalyzer()),
                entry(Language.BENGALI, new BengaliAnalyzer()),
                // analyzerClasses.put(Language.BRASILIAN, new BrazilianAnalyzer())
                entry(Language.CATALAN, new CatalanAnalyzer()),
                entry(Language.CHINESE_SIMPLIFIED, new CJKAnalyzer()),
                entry(Language.CHINESE_TRADITIONAL, new CJKAnalyzer()),
                entry(Language.JAPANESE, new CJKAnalyzer()),
                entry(Language.KOREAN, new CJKAnalyzer()),
                entry(Language.KURDISH, new SoraniAnalyzer()),
                entry(Language.CZECH, new CzechAnalyzer()),
                entry(Language.DANISH, new DanishAnalyzer()),
                entry(Language.GERMAN, new GermanAnalyzer()),
                entry(Language.GREEK, new GreekAnalyzer()),
                entry(Language.ENGLISH, new EnglishAnalyzer()),
                entry(Language.SPANISH, new SpanishAnalyzer()),
                entry(Language.ESTONIAN, new EstonianAnalyzer()),
                entry(Language.BASQUE, new BasqueAnalyzer()),
                entry(Language.PERSIAN, new PersianAnalyzer()),
                entry(Language.FINNISH, new FinnishAnalyzer()),
                entry(Language.FRENCH, new FrenchAnalyzer()),
                entry(Language.IRISH, new IrishAnalyzer()),
                entry(Language.GALICIAN, new GalicianAnalyzer()),
                entry(Language.HINDI, new HindiAnalyzer()),
                entry(Language.HUNGARIAN, new HungarianAnalyzer()),
                entry(Language.ARMENIAN, new ArmenianAnalyzer()),
                entry(Language.INDONESIAN, new IndonesianAnalyzer()),
                entry(Language.ITALIAN, new ItalianAnalyzer()),
                entry(Language.LITHUANIAN, new LithuanianAnalyzer()),
                entry(Language.LATVIAN, new LatvianAnalyzer()),
                entry(Language.NEPALI, new NepaliAnalyzer()),
                entry(Language.DUTCH, new DutchAnalyzer()),
                entry(Language.NORWEGIAN_BOKMAL, new NorwegianAnalyzer()),
                entry(Language.PORTUGUESE, new PortugueseAnalyzer()),
                entry(Language.ROMANIAN, new RomanianAnalyzer()),
                entry(Language.RUSSIAN, new RussianAnalyzer()),
                entry(Language.SERBIAN, new SerbianAnalyzer()),
                entry(Language.SWEDISH, new SwedishAnalyzer()),
                entry(Language.TAMIL, new TamilAnalyzer()),
                entry(Language.TELUGU, new TeluguAnalyzer()),
                entry(Language.THAI, new ThaiAnalyzer()),
                entry(Language.TURKISH, new TurkishAnalyzer())
        );
    }

    public Analyzer get(Language language) {
        return analyzerClasses.get(language);
    }

}
