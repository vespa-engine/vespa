// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.component.Version;
import com.yahoo.component.VersionSpecification;
import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.serialization.DocumentDeserializer;
import com.yahoo.document.serialization.DocumentDeserializerFactory;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.io.GrowableByteBuffer;
import java.util.logging.Level;
import com.yahoo.messagebus.Routable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * This class encapsulates the logic required to map routable type and version to a corresponding {@link
 * RoutableFactory}. It is owned and accessed through a {@link DocumentProtocol} instance. This class uses a factory
 * cache to reduce the latency of matching version specifications to actual versions when resolving factories.
 *
 * @author Simon Thoresen Hult
 */
final class RoutableRepository {

    private static final Logger log = Logger.getLogger(RoutableRepository.class.getName());
    private final CopyOnWriteHashMap<Integer, VersionMap> factoryTypes = new CopyOnWriteHashMap<>();
    private final CopyOnWriteHashMap<CacheKey, RoutableFactory> cache = new CopyOnWriteHashMap<>();

    public RoutableRepository() {}

    /**
     * Decodes a {@link Routable} from the given byte array. This uses the content of the byte array to dispatch the
     * decode request to the appropriate {@link RoutableFactory} that was previously registered.
     *
     * If a routable can not be decoded, this method returns null.
     *
     * @param version The version of the encoded routable.
     * @param data    The byte array containing the encoded routable.
     * @return The decoded routable.
     */
    Routable decode(DocumentTypeManager docMan, Version version, byte[] data) {
        if (data == null || data.length == 0) {
            log.log(Level.SEVERE, "Received empty byte array for deserialization.");
            return null;
        }
        if (version.getMajor() < 5) {
            log.log(Level.SEVERE,"Can not decode anything from (version " + version + "). Only major version 5 and up supported.");
            return null;
        }
        DocumentDeserializer in = DocumentDeserializerFactory.createHead(docMan, GrowableByteBuffer.wrap(data));


        int type = in.getInt(null);
        RoutableFactory factory = getFactory(version, type);
        if (factory == null) {
            log.log(Level.SEVERE,"No routable factory found for routable type " + type + " (version " + version + ").");
            return null;
        }
        Routable ret = factory.decode(in);
        if (ret == null) {
            log.log(Level.SEVERE,"Routable factory " + factory.getClass().getName() + " failed to deserialize " +
                                               "routable of type " + type + " (version " + version + ").\nData = " + Arrays.toString(data));
            return null;
        }
        return ret;
    }

    /**
     * Encodes a {@link Routable} into a byte array. This dispatches the encode request to the appropriate {@link
     * RoutableFactory} that was previously registered.
     *
     * If a routable can not be encoded, this method returns an empty byte array.
     *
     * @param version The version to encode the routable as.
     * @param obj     The routable to encode.
     * @return The byte array containing the encoded routable.
     */
    byte[] encode(Version version, Routable obj) {
        int type = obj.getType();
        RoutableFactory factory = getFactory(version, type);
        if (factory == null) {
            log.log(Level.SEVERE,"No routable factory found for routable type " + type + " (version " + version + ").");
            return new byte[0];
        }
        if (version.getMajor() < 5) {
            log.log(Level.SEVERE,"Can not encode routable type " + type + " (version " + version + "). Only major version 5 and up supported.");
            return new byte[0];
        }
        DocumentSerializer out= DocumentSerializerFactory.createHead(new GrowableByteBuffer(8192));

        out.putInt(null, type);
        if (!factory.encode(obj, out)) {
            log.log(Level.SEVERE, "Routable factory " + factory.getClass().getName() + " failed to serialize " +
                                    "routable of type " + type + " (version " + version + ").");
            return new byte[0];
        }
        byte[] ret = new byte[out.getBuf().position()];
        out.getBuf().rewind();
        out.getBuf().get(ret);
        return ret;
    }

    /**
     * Registers a routable factory for a given version and routable type.
     *
     * @param version The version specification that the given factory supports.
     * @param type    The routable type that the given factory supports.
     * @param factory The routable factory to register.
     */
    void putFactory(VersionSpecification version, int type, RoutableFactory factory) {
        VersionMap versionMap = factoryTypes.get(type);
        if (versionMap == null) {
            versionMap = new VersionMap();

            factoryTypes.put(type, versionMap);
        }
        if (versionMap.putFactory(version, factory)) {
            cache.clear();
        }
    }

    /**
     * Returns the routable factory for a given version and routable type.
     *
     * @param version The version that the factory must support.
     * @param type    The routable type that the factory must support.
     * @return The routable factory matching the criteria, or null.
     */
     private RoutableFactory getFactory(Version version, int type) {
        CacheKey cacheKey = new CacheKey(version, type);
        RoutableFactory factory = cache.get(cacheKey);
        if (factory != null) {
            return factory;
        }
        VersionMap versionMap = factoryTypes.get(type);
        if (versionMap == null) {
            return null;
        }
        factory = versionMap.getFactory(version);
        if (factory == null) {
            return null;
        }
        cache.put(cacheKey, factory);
        return factory;
    }

    /**
     * Returns a list of routable types that support the given version.
     *
     * @param version The version to return types for.
     * @return The list of supported types.
     */
    List<Integer> getRoutableTypes(Version version) {
        List<Integer> ret = new ArrayList<>();
        for (Map.Entry<Integer, VersionMap> entry : factoryTypes.entrySet()) {
            if (entry.getValue().getFactory(version) != null) {
                ret.add(entry.getKey());
            }
        }
        return ret;
    }

    /**
     * Internal helper class that implements a map from {@link VersionSpecification} to {@link RoutableFactory}.
     */
    private static class VersionMap {

        final Map<VersionSpecification, RoutableFactory> factoryVersions = new HashMap<>();

        boolean putFactory(VersionSpecification version, RoutableFactory factory) {
            return factoryVersions.put(version, factory) == null;
        }

        RoutableFactory getFactory(Version version) {
            VersionSpecification versionSpec = version.toSpecification();

            // Retrieve the factory with the highest version lower than or equal to actual version
            return factoryVersions.entrySet().stream()
                    // Drop factories that have a higher version than actual version
                    .filter(entry -> entry.getKey().compareTo(versionSpec) <= 0)

                    // Get the factory with the highest version
                    .max((entry1, entry2) -> entry1.getKey().compareTo(entry2.getKey()))
                    .map(Map.Entry::getValue)

                    // Return factory or null if no suitable factory found
                    .orElse(null);
        }
    }

    /**
     * Internal helper class that implements a cache key for mapping a {@link Version} and routable type to a {@link
     * RoutableFactory}.
     */
    private static class CacheKey {

        final Version version;
        final int type;

        CacheKey(Version version, int type) {
            this.version = version;
            this.type = type;
        }

        @Override
        public int hashCode() {
            return version.hashCode() + type;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey rhs = (CacheKey)obj;
            if (!version.equals(rhs.version)) {
                return false;
            }
            if (type != rhs.type) {
                return false;
            }
            return true;
        }
    }
}
