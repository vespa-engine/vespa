// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.searchdefinition.DistributableResource;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.tensor.TensorType;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Constant values for ranking/model execution tied to a rank profile,
 * to be distributed as files.
 *
 * @author bratseth
 */
public class FileDistributedConstants {

    private final FileRegistry fileRegistry;

    private final Map<String, DistributableConstant> constants = new LinkedHashMap<>();

    public FileDistributedConstants(FileRegistry fileRegistry, Collection<RankProfile.Constant> constants) {
        this.fileRegistry = fileRegistry;
        for (var constant : constants) {
            if (constant.valuePath().isPresent())
                add(new DistributableConstant(constant.name().simpleArgument().get(),
                                              constant.type(),
                                              constant.valuePath().get(),
                                              constant.pathType().get()));
        }
    }

    public void add(DistributableConstant constant) {
        constant.validate();
        constant.register(fileRegistry);
        String name = constant.getName();
        DistributableConstant prev = constants.putIfAbsent(name, constant);
        if ( prev != null )
            throw new IllegalArgumentException("Constant '" + name + "' defined twice");
    }

    public void putIfAbsent(DistributableConstant constant) {
        constant.validate();
        constant.register(fileRegistry);
        String name = constant.getName();
        constants.putIfAbsent(name, constant);
    }

    public void computeIfAbsent(String name, Function<? super String, ? extends DistributableConstant> createConstant) {
        constants.computeIfAbsent(name, key -> {
            DistributableConstant constant = createConstant.apply(key);
            constant.validate();
            constant.register(fileRegistry);
            return constant;
        });
    }

    /** Returns a read-only map of the constants in this indexed by name. */
    public Map<String, DistributableConstant> asMap() {
        return Collections.unmodifiableMap(constants);
    }

    public static class DistributableConstant extends DistributableResource {

        private TensorType tensorType;

        public DistributableConstant(String name, TensorType type, String fileName) {
            this(name, type, fileName, PathType.FILE);
        }

        public DistributableConstant(String name, TensorType type, String fileName, PathType pathType) {
            super(name, fileName, pathType);
            this.tensorType = type;
            validate();
        }

        public void setType(TensorType type) {
            this.tensorType = type;
        }

        public TensorType getTensorType() { return tensorType; }
        public String getType() { return tensorType.toString(); }

        public void validate() {
            super.validate();
            if (tensorType == null)
                throw new IllegalArgumentException("Ranking constant '" + getName() + "' must have a type.");
            if (tensorType.dimensions().stream().anyMatch(d -> d.isIndexed() && d.size().isEmpty()))
                throw new IllegalArgumentException("Illegal type in field " + getName() + " type " + tensorType +
                                                   ": Dense tensor dimensions must have a size");
        }

        @Override
        public String toString() {
            return super.toString() + "' of type '" + tensorType + "'";
        }

    }

}

