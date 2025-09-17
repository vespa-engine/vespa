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
        vespamalloc,
        mimalloc
    }

    static public String resolvePath(Impl impl) {
        return switch (impl) {
            case vespamalloc -> resolveVespaMallocPath();
            case mimalloc -> resolveMimallocPath();
        };
    }

    static public Optional<String> pathToLibrary(String impl) {
        if (impl == null || impl.isEmpty()) {
            return Optional.empty();
        }
        return switch (Impl.valueOf(impl)) {
            case vespamalloc -> Optional.of(resolveVespaMallocPath());
            case mimalloc -> Optional.of(resolveMimallocPath());
        };
    }

    static private String resolveMimallocPath() {
        // TODO(johsol): EXPERIMENTAL For testing mimalloc.
        return "/opt/vespa-deps/lib64/libmimalloc.so";
    }

    static private String resolveVespaMallocPath() {
        return Defaults.getDefaults().underVespaHome("lib64/vespa/malloc/libvespamalloc.so");
    }

}
