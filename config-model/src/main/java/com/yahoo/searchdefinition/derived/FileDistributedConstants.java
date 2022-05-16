// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.config.application.api.FileRegistry;
import com.yahoo.searchdefinition.DistributableResource;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Constant values for ranking/model execution tied to a rank profile,
 * to be distributed as files.
 *
 * @author bratseth
 */
public class FileDistributedConstants {

    private static final Logger log = Logger.getLogger(FileDistributedConstants.class.getName());
    private final Map<String, DistributableConstant> constants;

    public FileDistributedConstants(FileRegistry fileRegistry, Collection<RankProfile.Constant> constants) {
        Map<String, DistributableConstant> distributableConstants = new LinkedHashMap<>();
        for (var constant : constants) {
            if ( ! constant.valuePath().isPresent()) continue;

            var distributableConstant = new DistributableConstant(constant.name().simpleArgument().get(),
                                                                  constant.type(),
                                                                  constant.valuePath().get(),
                                                                  constant.pathType().get());
            distributableConstant.validate();
            distributableConstant.register(fileRegistry);
            distributableConstants.put(distributableConstant.getName(), distributableConstant);
        }
        this.constants = Collections.unmodifiableMap(distributableConstants);
    }

    /** Returns a read-only map of the constants in this indexed by name. */
    public Map<String, DistributableConstant> asMap() { return constants; }

    public void getConfig(RankingConstantsConfig.Builder builder) {
        for (var constant : constants.values()) {
            if ("".equals(constant.getFileReference()))
                log.warning("Illegal file reference " + constant); // Let tests pass ... we should find a better way
            else
                builder.constant(new RankingConstantsConfig.Constant.Builder()
                                         .name(constant.getName())
                                         .fileref(constant.getFileReference())
                                         .type(constant.getType()));
        }
    }

    public static class DistributableConstant extends DistributableResource {

        private final TensorType tensorType;

        public DistributableConstant(String name, TensorType type, String fileName) {
            this(name, type, fileName, PathType.FILE);
        }

        public DistributableConstant(String name, TensorType type, String fileName, PathType pathType) {
            super(name, fileName, pathType);
            this.tensorType = type;
            validate();
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

