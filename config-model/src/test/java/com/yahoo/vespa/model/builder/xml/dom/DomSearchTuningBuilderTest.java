// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.text.StringUtilities;
import com.yahoo.vespa.model.search.Tuning;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Arrays;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author geirst
 */
public class DomSearchTuningBuilderTest extends DomBuilderTest {

    private static final double DELTA = 0.000001;

    private static Element parseXml(String... xmlLines) {
        return parse("<tuning>",
                "<searchnode>",
                CollectionUtil.mkString(Arrays.asList(xmlLines), "\n"),
                "</searchnode>",
                "</tuning>");
    }

    private Tuning newTuning(String xml) {
        return createTuning(parse(xml));
    }

    private Tuning createTuning(Element xml) {
        DomSearchTuningBuilder b = new DomSearchTuningBuilder();
        return b.build(root, xml);
    }

    String getProtonCfg(Tuning tuning) {
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        tuning.getConfig(pb);
        return StringUtilities.implode(ConfigInstance.serialize(new ProtonConfig(pb)).toArray(new String[0]), "\n");
    }

    @Test
    public void requireThatNullDispatchIsSafe() {
        Tuning tuning = newTuning("<tuning />");
        assertNull(tuning.dispatch);
    }

    @Test
    public void requireThatEmptyDispatchIsSafe() {
        Tuning tuning = newTuning("<tuning><dispatch/></tuning>");
        Tuning.Dispatch dispatch = tuning.dispatch;
        assertNotNull(dispatch);
        assertNull(dispatch.maxHitsPerPartition);
    }

    @Test
    public void requireThatDispatchSettingsAreParsed() {
        Tuning tuning = createTuning(parse("<tuning>" +
                                           "  <dispatch>" +
                                           "    <max-hits-per-partition>69</max-hits-per-partition>" +
                                           "  </dispatch>" +
                                           "</tuning>"));
        Tuning.Dispatch dispatch = tuning.dispatch;
        assertNotNull(dispatch);
        assertNotNull(dispatch.maxHitsPerPartition);
        assertEquals(69, dispatch.maxHitsPerPartition.intValue());
    }

    @Test
    public void requireThatWeCanParseRequestThreadsTag() {
        Tuning t = createTuning(parseXml("<requestthreads>",
                "<search>123</search>",
                "<persearch>34</persearch>",
                "<summary>456</summary>",
                "</requestthreads>"));
        assertEquals(123, t.searchNode.threads.numSearchThreads.longValue());
        assertEquals(456, t.searchNode.threads.numSummaryThreads.longValue());
        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("numsearcherthreads 123"));
        assertThat(cfg, containsString("numthreadspersearch 34"));
        assertThat(cfg, containsString("numsummarythreads 456"));
     }

    @Test
    public void requireThatWeCanParseFlushStrategyTag() {
        Tuning t = createTuning(parseXml("<flushstrategy>","<native>",
                "<total>",
                "<maxmemorygain>900</maxmemorygain>",
                "<diskbloatfactor>8.7</diskbloatfactor>",
                "</total>",
                "<component>",
                "<maxmemorygain>600</maxmemorygain>",
                "<diskbloatfactor>5.4</diskbloatfactor>",
                "<maxage>300</maxage>",
                "</component>",
                "<transactionlog>",
                "<maxsize>1024</maxsize>",
                "</transactionlog>",
                "<conservative>",
                "<memory-limit-factor>0.6</memory-limit-factor>",
                "<disk-limit-factor>0.7</disk-limit-factor>",
                "</conservative>",
                "</native>","</flushstrategy>"));
        assertEquals(900, t.searchNode.strategy.totalMaxMemoryGain.longValue());
        assertEquals(8.7, t.searchNode.strategy.totalDiskBloatFactor.doubleValue(), DELTA);
        assertEquals(600, t.searchNode.strategy.componentMaxMemoryGain.longValue());
        assertEquals(5.4, t.searchNode.strategy.componentDiskBloatFactor.doubleValue(), DELTA);
        assertEquals(300, t.searchNode.strategy.componentMaxage.doubleValue(), DELTA);
        assertEquals(1024, t.searchNode.strategy.transactionLogMaxSize.longValue());
        assertEquals(0.6, t.searchNode.strategy.conservativeMemoryLimitFactor.doubleValue(), DELTA);
        assertEquals(0.7, t.searchNode.strategy.conservativeDiskLimitFactor.doubleValue(), DELTA);
        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("flush.memory.maxmemory 900"));
        assertThat(cfg, containsString("flush.memory.diskbloatfactor 8.7"));
        assertThat(cfg, containsString("flush.memory.each.maxmemory 600"));
        assertThat(cfg, containsString("flush.memory.each.diskbloatfactor 5.4"));
        assertThat(cfg, containsString("flush.memory.maxage.time 300"));
        assertThat(cfg, containsString("flush.memory.maxtlssize 1024"));
        assertThat(cfg, containsString("flush.memory.conservative.memorylimitfactor 0.6"));
        assertThat(cfg, containsString("flush.memory.conservative.disklimitfactor 0.7"));
    }

    @Test
    public void requireThatWeCanParseResizingTag() {
        Tuning t = createTuning(parseXml("<resizing>",
                "<initialdocumentcount>128</initialdocumentcount>",
                "</resizing>"));
        assertEquals(128, t.searchNode.resizing.initialDocumentCount.intValue());
        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("grow.initial 128"));
    }

    @Test
    public void requireThatWeCanParseIndexTag() {
        Tuning t = createTuning(parseXml("<index>", "<io>",
                "<write>directio</write>",
                "<read>normal</read>",
                "<search>mmap</search>",
                "</io>", "</index>"));
        assertEquals(Tuning.SearchNode.IoType.DIRECTIO, t.searchNode.index.io.write);
        assertEquals(Tuning.SearchNode.IoType.NORMAL, t.searchNode.index.io.read);
        assertEquals(Tuning.SearchNode.IoType.MMAP, t.searchNode.index.io.search);
        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("indexing.write.io DIRECTIO"));
        assertThat(cfg, containsString("indexing.read.io NORMAL"));
        assertThat(cfg, containsString("search.io MMAP"));
    }

    @Test
    public void requireThatWeCanParseAttributeTag() {
        Tuning t = createTuning(parseXml("<attribute>", "<io>",
                "<write>directio</write>",
                "</io>", "</attribute>"));
        assertEquals(Tuning.SearchNode.IoType.DIRECTIO, t.searchNode.attribute.io.write);
        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("attribute.write.io DIRECTIO"));
    }

    @Test
    public void requireThatWeCanParseSummaryTag() {
        Tuning t = createTuning(parseXml("<summary>",
                "<io>",
                "<write>directio</write>",
                "<read>directio</read>",
                "</io>",
                "<store>",
                "<cache>",
                "<maxsize>128</maxsize>",
                "<maxsize-percent>30.7</maxsize-percent>",
                "<initialentries>64</initialentries>",
                "<compression>",
                "<type>none</type>",
                "<level>3</level>",
                "</compression>",
                "</cache>",
                "<logstore>",
                "<maxfilesize>512</maxfilesize>",
                "<minfilesizefactor>0.3</minfilesizefactor>",
                "<numthreads>7</numthreads>",
                "<chunk>",
                "<maxsize>256</maxsize>",
                "<compression>",
                "<type>lz4</type>",
                "<level>5</level>",
                "</compression>",
                "</chunk>",
                "</logstore>",
                "</store>",
                "</summary>"));
        assertEquals(Tuning.SearchNode.IoType.DIRECTIO, t.searchNode.summary.io.write);
        assertEquals(Tuning.SearchNode.IoType.DIRECTIO, t.searchNode.summary.io.read);
        assertEquals(128, t.searchNode.summary.store.cache.maxSize.longValue());
        assertEquals(30.7, t.searchNode.summary.store.cache.maxSizePercent.doubleValue(), DELTA);
        assertEquals(Tuning.SearchNode.Summary.Store.Compression.Type.NONE,
                t.searchNode.summary.store.cache.compression.type);
        assertEquals(3, t.searchNode.summary.store.cache.compression.level.intValue());
        assertEquals(512, t.searchNode.summary.store.logStore.maxFileSize.longValue());
        assertEquals(0.3, t.searchNode.summary.store.logStore.minFileSizeFactor, DELTA);
        assertEquals(7, t.searchNode.summary.store.logStore.numThreads.intValue());
        assertEquals(256, t.searchNode.summary.store.logStore.chunk.maxSize.intValue());
        assertEquals(Tuning.SearchNode.Summary.Store.Compression.Type.LZ4,
                t.searchNode.summary.store.logStore.chunk.compression.type);
        assertEquals(5, t.searchNode.summary.store.logStore.chunk.compression.level.intValue());
        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("summary.write.io DIRECTIO"));
        assertThat(cfg, containsString("summary.read.io DIRECTIO"));
        assertThat(cfg, containsString("summary.cache.maxbytes 128"));
        assertThat(cfg, containsString("summary.cache.initialentries 64"));
        assertThat(cfg, containsString("summary.cache.compression.type NONE"));
        assertThat(cfg, containsString("summary.cache.compression.level 3"));
        assertThat(cfg, containsString("summary.log.maxfilesize 512"));
        assertThat(cfg, containsString("summary.log.minfilesizefactor 0.3"));
        assertThat(cfg, containsString("summary.log.chunk.maxbytes 256"));
        assertThat(cfg, containsString("summary.log.chunk.compression.type LZ4"));
        assertThat(cfg, containsString("summary.log.chunk.compression.level 5"));
    }

    @Test
    public void requireThatWeCanGiveSummaryCacheSizeInPercentage() {
        Tuning t = createTuning(parseXml("<summary>",
                "<store>",
                "<cache>",
                "<maxsize-percent>30.7</maxsize-percent>",
                "</cache>",
                "</store>",
                "</summary>"));

        assertNull(t.searchNode.summary.store.cache.maxSize);
        assertEquals(30.7, t.searchNode.summary.store.cache.maxSizePercent.doubleValue(),DELTA);

        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("summary.cache.maxbytes -30"));
    }


    @Test
    public void requireThatWeCanParseInitializeTag() {
        Tuning t = createTuning(parseXml("<initialize>",
                "<threads>7</threads>",
                "</initialize>"));
        assertEquals(7, t.searchNode.initialize.threads.intValue());
        String cfg = getProtonCfg(t);
        assertThat(cfg, containsString("initialize.threads 7"));
    }

    @Test
    public void requireThatWeCanParseFeedingTag() {
        Tuning t = createTuning(parseXml("<feeding>",
                "<concurrency>0.7</concurrency>",
                "</feeding>"));
        assertEquals(0.7, t.searchNode.feeding.concurrency.doubleValue(), DELTA);
        assertThat(getProtonCfg(t), containsString("feeding.concurrency 0.7"));
    }

}
