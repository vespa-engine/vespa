package com.yahoo.language.opennlp;

import ai.vespa.opennlp.OpenNlpConfig;
import com.yahoo.language.Language;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ChineseSegmentationTest {

    private final String text = "是一个展示雅，目前在测试阶段。";

    @Test
    public void testChineseSegmentation() {
        var linguistics = new OpenNlpLinguistics(new OpenNlpConfig.Builder().cjk(true).build());
        List<String> tokens;

        tokens = asList(linguistics.getTokenizer().tokenize(text, Language.CHINESE_SIMPLIFIED, StemMode.ALL, true));
        assertEquals(9, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, ，, 目前, 在, 测试阶段, 。]", tokens.toString());

        tokens = asList(linguistics.getTokenizer().tokenize(text, Language.CHINESE_TRADITIONAL, StemMode.ALL, true));
        assertEquals(9, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, ，, 目前, 在, 测试阶段, 。]", tokens.toString());

        tokens = linguistics.getSegmenter().segment(text, Language.CHINESE_SIMPLIFIED);
        assertEquals(7, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, 目前, 在, 测试阶段]", tokens.toString());

        var stems = linguistics.getStemmer().stem(text, StemMode.ALL, Language.CHINESE_SIMPLIFIED);
        assertEquals(7, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, 目前, 在, 测试阶段]", tokens.toString());
    }

    @Test
    public void testChineseSegmentationWithGrams() {
        var linguistics = new OpenNlpLinguistics(new OpenNlpConfig.Builder().cjk(true).createCjkGrams(true).build());
        List<String> tokens;

        tokens = asList(linguistics.getTokenizer().tokenize(text, Language.CHINESE_SIMPLIFIED, StemMode.ALL, true));
        assertEquals(11, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, ，, 目前, 在, 测试, 阶段, 测试阶段, 。]", tokens.toString());

        tokens = asList(linguistics.getTokenizer().tokenize(text, Language.CHINESE_TRADITIONAL, StemMode.ALL, true));
        assertEquals(11, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, ，, 目前, 在, 测试, 阶段, 测试阶段, 。]", tokens.toString());

        tokens = linguistics.getSegmenter().segment(text, Language.CHINESE_SIMPLIFIED);
        assertEquals(7, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, 目前, 在, 测试阶段]", tokens.toString());

        var stems = linguistics.getStemmer().stem(text, StemMode.ALL, Language.CHINESE_SIMPLIFIED);
        assertEquals(7, tokens.size());
        assertEquals("[是, 一个, 展示, 雅, 目前, 在, 测试阶段]", tokens.toString());
    }

    @Test
    public void testOtherLanguagesWorksAsUsualWithChineseSegmentation() {
        var linguistics = new OpenNlpLinguistics(new OpenNlpConfig.Builder().cjk(true).build());
        List<String> tokens;

        tokens = asList(linguistics.getTokenizer().tokenize(text, Language.ENGLISH, StemMode.ALL, true));
        assertEquals(4, tokens.size());
        assertEquals("[是一个展示雅, ,, 目前在测试阶段, 。]", tokens.toString());

        tokens = asList(linguistics.getTokenizer().tokenize("english texts", Language.ENGLISH, StemMode.ALL, true));
        assertEquals(3, tokens.size());
        assertEquals("[english,  , text]", tokens.toString());
    }

    private List<String> asList(Iterable<Token> tokens) {
        List<String> list = new ArrayList<>();
        tokens.forEach(token -> list.add(token.getTokenString()));
        return list;
    }

}
