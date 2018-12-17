package com.yahoo.vespa.hosted.controller.api.integration.organization;

/**
 * Allows sending e-mail from a particular <code>user@domain</code>.
 *
 * @author jonmv
 */
public interface Mailer {

    /** Sends the given mail as the configured user@domain. */
    void send(Mail mail);

    /** Returns the user this is configured to use. */
    String user();

    /** Returns the domain this is configured to use. */
    String domain();

}
