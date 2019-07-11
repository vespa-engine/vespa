// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.persistence;

import com.google.inject.Inject;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.json.FlagData;
import org.apache.curator.framework.recipes.cache.ChildData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author hakonhall
 */
public class FlagsDb {

    private static final Path ROOT_PATH = Path.fromString("/flags/v1");

    private final Curator curator;
    private final Curator.DirectoryCache cache;

    @Inject
    public FlagsDb(Curator curator) {
        this.curator = curator;
        curator.create(ROOT_PATH);
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        this.cache = curator.createDirectoryCache(ROOT_PATH.getAbsolute(), true, false, executorService);
        cache.start();
    }

    /** Get the String value of the flag. */
    public Optional<FlagData> getValue(FlagId flagId) {
        return Optional.ofNullable(cache.getCurrentData(getZkPathFor(flagId)))
                       .map(ChildData::getData)
                       .map(FlagData::deserializeUtf8Json);
    }

    /** Set the String value of the flag. */
    public void setValue(FlagId flagId, FlagData data) {
        curator.set(getZkPathFor(flagId), data.serializeToUtf8Json());
    }

    /** Get all flags that have been set. */
    public Map<FlagId, FlagData> getAllFlags() {
        List<ChildData> dataList = cache.getCurrentData();
        return dataList.stream()
                       .map(ChildData::getData)
                       .map(FlagData::deserializeUtf8Json)
                       .collect(Collectors.toMap(FlagData::id, Function.identity()));
    }

    /** Remove the flag value if it exists. */
    public void removeValue(FlagId flagId) {
        curator.delete(getZkPathFor(flagId));
    }

    private static Path getZkPathFor(FlagId flagId) {
        return ROOT_PATH.append(flagId.toString());
    }

}
