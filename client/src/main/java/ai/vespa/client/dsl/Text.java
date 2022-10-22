// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.client.dsl;

import java.util.Locale;

/**
 * Text utility functions.
 *
 * @author arnej
 */
final class Text {

    public static String format(String format, Object... args) {
        return String.format(Locale.US, format, args);
    }

}
