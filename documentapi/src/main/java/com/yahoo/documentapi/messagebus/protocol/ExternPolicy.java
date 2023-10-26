// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.routing.Hop;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.routing.RoutingContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This policy implements the necessary logic to communicate with an external Vespa application and resolve its list of
 * recipients using that other application's slobrok servers.
 *
 * @author Simon Thoresen Hult
 */
public class ExternPolicy implements DocumentProtocolRoutingPolicy {

    private Supervisor orb = null;
    private Mirror mirror = null;
    private String pattern = null;
    private String session = null;
    private final String error;
    private int offset = 0;
    private int generation = 0;
    private final List<Hop> recipients = new ArrayList<>();
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    /**
     * Constructs a new instance of this policy. The argument given is the connection spec to the slobrok to use for
     * resolving recipients, as well as the pattern to use when querying. This constructor does _not_ wait for the
     * mirror to become ready.
     *
     * @param arg The slobrok connection spec.
     */
    public ExternPolicy(String arg) {
        if (arg == null || arg.length() == 0) {
            error = "Expected parameter, got empty string.";
            return;
        }
        String[] args = arg.split(";", 2);
        if (args.length != 2 || args[0].length() == 0 || args[1].length() == 0) {
            error = "Expected parameter on the form '<spec>;<pattern>', got '" + arg + "'.";
            return;
        }
        int pos = args[1].lastIndexOf('/');
        if (pos < 0) {
            error = "Expected pattern on the form '<service>/<session>', got '" + args[1] + "'.";
            return;
        }
        SlobrokList slobroks = new SlobrokList();
        slobroks.setup(args[0].split(","));
        pattern = args[1];
        session = pattern.substring(pos);
        orb = new Supervisor(new Transport("externpolicy"));
        orb.setDropEmptyBuffers(true);
        mirror = new Mirror(orb, slobroks);
        error = null;
    }

    /**
     * This is a safety mechanism to allow the constructor to fail and signal that it can not be used.
     *
     * @return The error string, or null if no error.
     */
    public String getError() {
        return error;
    }

    /**
     * Returns the slobrok mirror used by this policy to resolve external recipients.
     *
     * @return The external mirror.
     */
    public Mirror getMirror() {
        return mirror;
    }

    /**
     * Returns the appropriate recipient hop. This method provides synchronized access to the internal mirror.
     *
     * @return The recipient hop to use.
     */
    private synchronized Hop getRecipient() {
        update();
        if (recipients.isEmpty()) {
            return null;
        }
        int offset = ++this.offset & Integer.MAX_VALUE; // mask signed bit because of modulo
        return new Hop(recipients.get(offset % recipients.size()));
    }

    /**
     * Updates the list of matching recipients by querying the extern slobrok.
     */
    private void update() {
        int upd = mirror.updates();
        if (generation != upd) {
            generation = upd;
            recipients.clear();
            List<Mirror.Entry> arr = mirror.lookup(pattern);
            for (Mirror.Entry entry : arr) {
                recipients.add(Hop.parse(entry.getSpecString() + session));
            }
        }
    }

    @Override
    public void select(RoutingContext ctx) {
        if (error != null) {
            ctx.setError(DocumentProtocol.ERROR_POLICY_FAILURE, error);
        } else if (mirror.ready()) {
            Hop hop = getRecipient();
            if (hop != null) {
                Route route = new Route(ctx.getRoute());
                route.setHop(0, hop);
                ctx.addChild(route);
            } else {
                ctx.setError(ErrorCode.NO_ADDRESS_FOR_SERVICE,
                             "Could not resolve any recipients from '" + pattern + "'.");
            }
        } else {
            ctx.setError(ErrorCode.APP_TRANSIENT_ERROR, "Extern slobrok not ready.");
        }
    }

    @Override
    public void merge(RoutingContext ctx) {
        DocumentProtocol.merge(ctx);
    }

    @Override
    public void destroy() {
        if (destroyed.getAndSet(true)) throw new RuntimeException("Already destroyed");
        mirror.shutdown();
        orb.transport().shutdown().join();
    }
}
