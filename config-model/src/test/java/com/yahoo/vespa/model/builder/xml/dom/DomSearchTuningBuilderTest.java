// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.collections.CollectionUtil;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.config.model.builder.xml.test.DomBuilderTest;
import com.yahoo.vespa.model.search.Tuning;
import org.junit.Test;
import org.w3c.dom.Element;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        return b.build(root.getDeployState(), root, xml);
    }

    private ProtonConfig getProtonCfg(Tuning tuning) {
        ProtonConfig.Builder pb = new ProtonConfig.Builder();
        tuning.getConfig(pb);
        return new ProtonConfig(pb);
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
        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.numsearcherthreads(), 123);
        assertEquals(cfg.numthreadspersearch(), 34);
        assertEquals(cfg.numsummarythreads(), 456);
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
        assertEquals(8.7, t.searchNode.strategy.totalDiskBloatFactor, DELTA);
        assertEquals(600, t.searchNode.strategy.componentMaxMemoryGain.longValue());
        assertEquals(5.4, t.searchNode.strategy.componentDiskBloatFactor, DELTA);
        assertEquals(300, t.searchNode.strategy.componentMaxage, DELTA);
        assertEquals(1024, t.searchNode.strategy.transactionLogMaxSize.longValue());
        assertEquals(0.6, t.searchNode.strategy.conservativeMemoryLimitFactor, DELTA);
        assertEquals(0.7, t.searchNode.strategy.conservativeDiskLimitFactor, DELTA);
        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.flush().memory().maxmemory(), 900);
        assertEquals(cfg.flush().memory().diskbloatfactor(),  8.7, DELTA);
        assertEquals(cfg.flush().memory().each().maxmemory(), 600);
        assertEquals(cfg.flush().memory().each().diskbloatfactor(), 5.4, DELTA);
        assertEquals(cfg.flush().memory().maxage().time(), 300, DELTA);
        assertEquals(cfg.flush().memory().maxtlssize(), 1024);
        assertEquals(cfg.flush().memory().conservative().memorylimitfactor(), 0.6, DELTA);
        assertEquals(cfg.flush().memory().conservative().disklimitfactor(), 0.7, DELTA);
    }

    @Test
    public void requireThatWeCanParseResizingTag() {
        Tuning t = createTuning(parseXml("<resizing>",
                "<initialdocumentcount>128</initialdocumentcount>",
                "<amortize-count>13</amortize-count>",
                "</resizing>"));
        assertEquals(128, t.searchNode.resizing.initialDocumentCount.intValue());
        assertEquals(13, t.searchNode.resizing.amortizeCount.intValue());
    }

    @Test
    public void requireThatWeCanParseIndexTag() {
        Tuning t = createTuning(parseXml("<index>", "<io>",
                "<write>directio</write>",
                "<read>normal</read>",
                "<search>mmap</search>",
                "</io>",
                "<warmup>" +
                "<time>178</time>",
                "<unpack>true</unpack>",
                "</warmup>",
                "</index>"));
        assertEquals(Tuning.SearchNode.IoType.DIRECTIO, t.searchNode.index.io.write);
        assertEquals(Tuning.SearchNode.IoType.NORMAL, t.searchNode.index.io.read);
        assertEquals(Tuning.SearchNode.IoType.MMAP, t.searchNode.index.io.search);
        assertEquals(178, t.searchNode.index.warmup.time, DELTA);
        assertTrue(t.searchNode.index.warmup.unpack);
        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.indexing().write().io(), ProtonConfig.Indexing.Write.Io.DIRECTIO);
        assertEquals(cfg.indexing().read().io(), ProtonConfig.Indexing.Read.Io.NORMAL);
        assertEquals(cfg.index().warmup().time(), 178, DELTA);
        assertTrue(cfg.index().warmup().unpack());
    }

    @Test
    public void requireThatWeCanPopulateIndex() {
        Tuning t = createTuning(parseXml("<index>", "<io>",
                "<search>populate</search>",
                "</io>",
                "</index>"));
        assertEquals(Tuning.SearchNode.IoType.POPULATE, t.searchNode.index.io.search);

        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.indexing().write().io(), ProtonConfig.Indexing.Write.Io.DIRECTIO);
        assertEquals(cfg.indexing().read().io(), ProtonConfig.Indexing.Read.Io.DIRECTIO);
        assertEquals(cfg.search().mmap().options().size(), 1);
        assertEquals(cfg.search().mmap().options().get(0), ProtonConfig.Search.Mmap.Options.POPULATE);
    }


    @Test
    public void requireThatWeCanParseRemovedDBTag() {
        Tuning t = createTuning(parseXml("<removed-db>", "<prune>",
                "<age>19388</age>",
                "<interval>193</interval>",
                "</prune>", "</removed-db>"));
        assertEquals(19388, t.searchNode.removedDB.prune.age, DELTA);
        assertEquals(193, t.searchNode.removedDB.prune.interval, DELTA);
        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.pruneremoveddocumentsinterval(), 193, DELTA);
        assertEquals(cfg.pruneremoveddocumentsage(), 19388, DELTA);
    }

    @Test
    public void requireThatWeCanParseAttributeTag() {
        Tuning t = createTuning(parseXml("<attribute>", "<io>",
                "<write>directio</write>",
                "</io>", "</attribute>"));
        assertEquals(Tuning.SearchNode.IoType.DIRECTIO, t.searchNode.attribute.io.write);
        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.attribute().write().io(), ProtonConfig.Attribute.Write.Io.DIRECTIO);
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
        assertEquals(30.7, t.searchNode.summary.store.cache.maxSizePercent, DELTA);
        assertEquals(Tuning.SearchNode.Summary.Store.Compression.Type.NONE,
                t.searchNode.summary.store.cache.compression.type);
        assertEquals(3, t.searchNode.summary.store.cache.compression.level.intValue());
        assertEquals(512, t.searchNode.summary.store.logStore.maxFileSize.longValue());
        assertEquals(0.3, t.searchNode.summary.store.logStore.minFileSizeFactor, DELTA);
        assertEquals(256, t.searchNode.summary.store.logStore.chunk.maxSize.intValue());
        assertEquals(Tuning.SearchNode.Summary.Store.Compression.Type.LZ4,
                t.searchNode.summary.store.logStore.chunk.compression.type);
        assertEquals(5, t.searchNode.summary.store.logStore.chunk.compression.level.intValue());
        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.summary().write().io(), ProtonConfig.Summary.Write.Io.DIRECTIO);
        assertEquals(cfg.summary().read().io(), ProtonConfig.Summary.Read.Io.DIRECTIO);
        assertEquals(cfg.summary().cache().maxbytes(), 128);
        assertEquals(cfg.summary().cache().initialentries(), 64);
        assertEquals(cfg.summary().cache().compression().type(), ProtonConfig.Summary.Cache.Compression.Type.NONE);
        assertEquals(cfg.summary().cache().compression().level(), 3);
        assertEquals(cfg.summary().log().maxfilesize(), 512);
        assertEquals(cfg.summary().log().minfilesizefactor(), 0.3, DELTA);
        assertEquals(cfg.summary().log().chunk().maxbytes(), 256);
        assertEquals(cfg.summary().log().chunk().compression().type(), ProtonConfig.Summary.Log.Chunk.Compression.Type.LZ4);
        assertEquals(cfg.summary().log().chunk().compression().level(), 5);
        assertEquals(cfg.summary().log().compact().compression().type(), ProtonConfig.Summary.Log.Compact.Compression.Type.LZ4);
        assertEquals(cfg.summary().log().compact().compression().level(), 5);
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
        assertEquals(30.7, t.searchNode.summary.store.cache.maxSizePercent,DELTA);

        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.summary().cache().maxbytes(), -30);
    }

    @Test
    public void requireThatWeCanPopulateSummary() {
        Tuning t = createTuning(parseXml("<summary>",
                "<io>",
                "<read>populate</read>",
                "</io>",
                "</summary>"));

        assertEquals(Tuning.SearchNode.IoType.POPULATE, t.searchNode.summary.io.read);

        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(ProtonConfig.Summary.Read.Io.MMAP, cfg.summary().read().io());
        assertEquals(ProtonConfig.Summary.Read.Mmap.Options.POPULATE, cfg.summary().read().mmap().options().get(0));
    }


    @Test
    public void requireThatWeCanParseInitializeTag() {
        Tuning t = createTuning(parseXml("<initialize>",
                "<threads>7</threads>",
                "</initialize>"));
        assertEquals(7, t.searchNode.initialize.threads.intValue());
        ProtonConfig cfg = getProtonCfg(t);
        assertEquals(cfg.initialize().threads(), 7);
    }

    @Test
    public void requireThatWeCanParseFeedingTag() {
        Tuning t = createTuning(parseXml("<feeding>",
                "<concurrency>0.7</concurrency>",
                "</feeding>"));
        assertEquals(0.7, t.searchNode.feeding.concurrency, DELTA);
        assertEquals(getProtonCfg(t).feeding().concurrency(), 0.7, DELTA);
    }

}
