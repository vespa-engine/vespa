package com.yahoo.config.model;


import com.yahoo.vespa.defaults.Defaults;

import java.util.Optional;

/**
 * Resolves malloc implementation in config model.
 * <p>
 * EXPERIMENTAL: Currently used for testing mimalloc. Unfinished.
 *
 * @author johsol
 */
public class MallocImplResolver {

    public enum Impl {
        mimalloc,
        vespamalloc,
        vespamallocd,
        vespamallocdst
    }

    static public Optional<String> pathToLibrary(String impl) {
        if (impl == null || impl.isEmpty()) {
            return Optional.empty();
        }
        return switch (Impl.valueOf(impl)) {
            case mimalloc -> Optional.of(resolveMimallocPath());
            case vespamalloc -> Optional.of(resolveVespaMallocPath());
            case vespamallocd -> Optional.of(resolveVespaMallocDebugPath());
            case vespamallocdst -> Optional.of(resolveVespaMallocDstPath());
        };
    }

    static private String resolveMimallocPath() {
        return "/opt/vespa-deps/lib64/libmimalloc.so";
    }

    static private String resolveVespaMallocPath() {
        return Defaults.getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so");
    }

    static private String resolveVespaMallocDebugPath() {
        return Defaults.getDefaults().underVespaHome("lib64/vespa/malloc/libvespamallocd.so");
    }

    static private String resolveVespaMallocDstPath() {
        return Defaults.getDefaults().underVespaHome("lib64/vespa/malloc/libvespamallocdst16.so");
    }



}
