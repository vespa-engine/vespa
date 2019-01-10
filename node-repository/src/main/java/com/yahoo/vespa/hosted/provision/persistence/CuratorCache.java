package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.path.Path;

import java.util.List;
import java.util.Optional;

public abstract class CuratorCache {

    public abstract List<String> getChildren(Path path);

    public abstract Optional<byte[]> getData(Path path);

}