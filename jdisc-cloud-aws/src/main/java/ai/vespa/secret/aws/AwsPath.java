/*
 * // Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
 *
 */

package ai.vespa.secret.aws;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

/**
 * @author gjoranv
 */
public class AwsPath extends PatternedStringWrapper<AwsPath> {

    // The limit for a path in AWS is 512 chars, but we can limit ourselves to
    // "/tenant-secret.<system>.<tenant>/".
    // Here, we assume 128 chars tenant name and max 30 chars system name
    private static final Pattern namePattern = Pattern.compile("/[/.a-zA-Z0-9_-]{0,173}");


    private AwsPath(String name) {
        super(name, namePattern, "AWS path");
    }

    /** Creates a new path from the given elements, adding the leading and trailing slashes */
    public static AwsPath of(String... elements) {
        if (elements != null && elements.length > 0 && elements[0] != null && ! elements[0].isEmpty()) {
            return new AwsPath(
                    Stream.of(elements)
                            .map(String::toLowerCase)
                            // TODO: consider using '.' as delimiter, to prevent deep paths
                            .collect(joining("/", "/", "/")));

        }
        return new AwsPath("/");
    }

    public static AwsPath root() {
        return new AwsPath("/");
    }

    /** Creates a new instance from the given string, that is assumed to contain the leading and trailing slashes */
    public static AwsPath fromAwsPathString(String path) {
        return new AwsPath(path);
    }

}
