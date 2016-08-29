// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.osgi;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.net.HostName;
import com.yahoo.log.LogLevel;
import com.yahoo.osgi.Osgi;
import com.yahoo.yolean.Exceptions;
import org.osgi.framework.Bundle;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * An rpc adaptor to container Osgi commands.
 *
 * @author bratseth
 */
public class ContainerRpcAdaptor extends AbstractRpcAdaptor {

    private static final Logger log = Logger.getLogger(ContainerRpcAdaptor.class.getName());

    private Acceptor acceptor;
    private final Supervisor supervisor;

    private final Osgi osgi;
    private final String hostname;

    private Optional<String> slobrokId = Optional.empty();
    private Optional<Register> slobrokRegistrator = Optional.empty();

    public ContainerRpcAdaptor(Osgi osgi) {
        this.osgi = osgi;
        this.supervisor = new Supervisor(new Transport());
        this.hostname = HostName.getLocalhost();

        bindCommands(supervisor);
    }

    public void list(Request request) {
        try {
            StringBuilder buffer=new StringBuilder("Installed bundles:");
            for (Bundle bundle : osgi.getBundles()) {
                if (bundle.getSymbolicName().equals("system.bundle")) continue;
                buffer.append("\n");
                buffer.append(bundle.getSymbolicName());
                buffer.append(" (");
                buffer.append(bundle.getLocation());
                buffer.append(")");
            }
            request.returnValues().add(new StringValue(buffer.toString()));
        }
        catch (Exception e) {
            request.setError(ErrorCode.METHOD_FAILED,Exceptions.toMessageString(e));
        }
    }

    public void bindCommands(Supervisor supervisor) {
        supervisor.addMethod(new Method("list","","s",this,"list"));
    }

    public synchronized void listen(int port) {
        Spec spec = new Spec(port);
        try {
            acceptor = supervisor.listen(spec);
            log.log(LogLevel.DEBUG, "Added new rpc server listening at" + " port '" + port + "'.");
        } catch (ListenFailedException e) {
            throw new RuntimeException("Could not create rpc server listening on " + spec, e);
        }
    }

    public synchronized void setSlobrokId(String slobrokId) {
        this.slobrokId = Optional.of(slobrokId);
    }

    public synchronized void registerInSlobrok(List<String> slobrokConnectionSpecs) {
        shutdownSlobrokRegistrator();

        if (slobrokConnectionSpecs.isEmpty()) {
            return;
        }

        if (!slobrokId.isPresent()) {
            throw new AssertionError("Slobrok id must be set first");
        }

        SlobrokList slobrokList = new SlobrokList();
        slobrokList.setup(slobrokConnectionSpecs.stream().toArray(String[]::new));

        Spec mySpec = new Spec(hostname, acceptor.port());

        Register register = new Register(supervisor, slobrokList, mySpec);
        register.registerName(slobrokId.get());
        slobrokRegistrator = Optional.of(register);

        log.log(LogLevel.INFO, "Registered name '" + slobrokId.get() + "' at " + mySpec + " with: " + slobrokList);
    }

    private synchronized void shutdownSlobrokRegistrator() {
        slobrokRegistrator.ifPresent(Register::shutdown);
        slobrokRegistrator = Optional.empty();
    }

    public synchronized void shutdown() {
        shutdownSlobrokRegistrator();

        if (acceptor != null) {
            acceptor.shutdown().join();
        }
        supervisor.transport().shutdown().join();
    }


    public synchronized void bindRpcAdaptor(AbstractRpcAdaptor adaptor) {
        adaptor.bindCommands(supervisor);
    }

}
