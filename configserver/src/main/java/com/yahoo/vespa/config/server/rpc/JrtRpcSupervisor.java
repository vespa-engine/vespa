// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.rpc;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.CryptoEngine;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.NullCryptoEngine;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.TlsCryptoEngine;
import com.yahoo.jrt.Transport;
import com.yahoo.security.tls.TransportSecurityOptions;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * A wrapper around {@link Supervisor} to facilitate dual RPC setup (secure and insecure endpoint on separate ports).
 *
 * @author bjorncs
 */
public class JrtRpcSupervisor {

    private final List<SupervisorAndSpec> supervisors;

    public JrtRpcSupervisor(int insecurePort, int securePort, int maxOutputBufferSize) {
        this.supervisors = createSupervisors(insecurePort, securePort, maxOutputBufferSize);
    }

    public List<Integer> ports() {
        return apply((supervisor, spec) -> spec.port());
    }

    public List<Acceptor> listen() throws ListenFailedException {
        return apply((supervisor, spec) -> {
            try {
                return supervisor.listen(spec);
            } catch (ListenFailedException e) {
                uncheckedThrow(e); // rethrow checked exception by tricking the compiler
                return null; // will never happen
            }
        });
    }

    public void awaitTransportFinish() {
        accept((supervisor, spec) -> supervisor.transport().join());
    }

    public List<Spec> specs() {
        return apply((supervisor, spec) -> spec);
    }

    public void awaitTransportShutdown() {
        accept((supervisor, spec) -> supervisor.transport().shutdown().join());
    }

    public void addMethod(Method method) {
        accept((supervisor, spec) -> supervisor.addMethod(method));
    }

    private <T> List<T> apply(BiFunction<Supervisor, Spec, T> operation) {
        return supervisors.stream()
                .map(s -> operation.apply(s.supervisor, s.spec))
                .collect(toList());
    }

    private void accept(BiConsumer<Supervisor, Spec> operation) {
        supervisors.forEach(s -> operation.accept(s.supervisor, s.spec));
    }

    private static List<SupervisorAndSpec> createSupervisors(int insecurePort, int securePort, int maxOutputBufferSize) {
        List<SupervisorAndSpec> supervisors = new ArrayList<>();
        if (TransportSecurityUtils.isTransportSecurityEnabled()) {
            TransportSecurityOptions options = TransportSecurityUtils.getOptions().get();
            if (TransportSecurityUtils.isConfigInsecureMixedModeEnabled()) {
                supervisors.add(createSupervisor(insecurePort, new NullCryptoEngine()));
                supervisors.add(createSupervisor(securePort, new TlsCryptoEngine(options)));
            } else {
                supervisors.add(createSupervisor(insecurePort, new TlsCryptoEngine(options)));
            }
        } else {
            supervisors.add(createSupervisor(insecurePort, new NullCryptoEngine()));
        }
        supervisors.forEach(s -> s.supervisor.setMaxOutputBufferSize(maxOutputBufferSize));
        return supervisors;
    }

    private static SupervisorAndSpec createSupervisor(int port, CryptoEngine engine) {
        return new SupervisorAndSpec(new Supervisor(new Transport(engine)), new Spec(null, port));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void uncheckedThrow(Throwable t) throws T {
        throw (T)t;
    }

    private static class SupervisorAndSpec {
        final Supervisor supervisor;
        final Spec spec;

        SupervisorAndSpec(Supervisor supervisor, Spec spec) {
            this.supervisor = supervisor;
            this.spec = spec;
        }
    }
}
