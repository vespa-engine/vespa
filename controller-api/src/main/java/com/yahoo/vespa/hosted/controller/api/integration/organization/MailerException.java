// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

/**
 * MailerException wrap all possible Mailer implementation exceptions
 *
 * @author enygaard
 */
public class MailerException extends RuntimeException {

    public MailerException(Throwable ex) {
        super(ex);
    }
}
