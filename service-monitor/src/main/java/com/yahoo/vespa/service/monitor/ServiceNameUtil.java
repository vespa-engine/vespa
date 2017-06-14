// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class provides utilities for working with slobrok-registered service names.
 *
 * @author bakksjo
 */
public class ServiceNameUtil {
    // Utility class; prevents instantiation.
    private ServiceNameUtil() {
    }

    static Set<String> convertSlobrokServicesToConfigIds(final Set<String> registeredServices) {
        return registeredServices.stream()
                        .map(ALL_RECOGNIZER)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toSet());
    }

    // This is basically a typedef.
    private interface ServiceNameRecognizer extends Function<String, Optional<String>> {}

    private static class RegexpServiceNameRecognizer implements ServiceNameRecognizer {
        private final Pattern pattern;
        private final Function<Matcher, String> nameConverter;

        public RegexpServiceNameRecognizer(
                final String patternString,
                final Function<Matcher, String> nameConverter) {
            this.pattern = Pattern.compile(patternString);
            this.nameConverter = nameConverter;
        }

        @Override
        public Optional<String> apply(final String serviceName) {
            final Matcher matcher = pattern.matcher(serviceName);
            if (!matcher.matches()) {
                return Optional.empty();
            }
            return Optional.of(nameConverter.apply(matcher));
        }
    }

    private static class SingleGroupRegexpServiceNameRecognizer extends RegexpServiceNameRecognizer {
        public SingleGroupRegexpServiceNameRecognizer(final String patternString) {
            super(patternString, matcher -> matcher.group(1));
        }
    }


    // TODO: The regexps below almost certainly hard-code names that are dynamically set in config.

    // storage/cluster.basicsearch/storage/0 -> basicsearch/storage/0
    static final ServiceNameRecognizer STORAGENODE_RECOGNIZER = new SingleGroupRegexpServiceNameRecognizer(
            "^storage/cluster\\.([^/]+/storage/[^/]+)$");

    // storage/cluster.basicsearch/distributor/0 -> basicsearch/distributor/0
    static final ServiceNameRecognizer DISTRIBUTOR_RECOGNIZER = new SingleGroupRegexpServiceNameRecognizer(
            "^storage/cluster\\.([^/]+/distributor/[^/]+)$");

    // docproc/cluster.basicsearch.indexing/0/chain.indexing -> docproc/cluster.basicsearch.indexing/0
    static final ServiceNameRecognizer DOCPROC_RECOGNIZER = new SingleGroupRegexpServiceNameRecognizer(
            "^(docproc/cluster\\.[^/.]+\\.indexing/[^/]+)/.*$");

    // basicsearch/search/cluster.basicsearch/0/realtimecontroller -> basicsearch/search/cluster.basicsearch/0
    static final ServiceNameRecognizer SEARCH_RECOGNIZER = new SingleGroupRegexpServiceNameRecognizer(
            "^(basicsearch/search/cluster.basicsearch/[^/.]+)/.*$");

    static final ServiceNameRecognizer ALL_RECOGNIZER = serviceName -> Stream.of(
            STORAGENODE_RECOGNIZER,
            DISTRIBUTOR_RECOGNIZER,
            DOCPROC_RECOGNIZER,
            SEARCH_RECOGNIZER)
            .map(recognizer -> recognizer.apply(serviceName))
            .filter(optional -> optional.isPresent())
            .findFirst()
            .orElse(Optional.empty());
}
