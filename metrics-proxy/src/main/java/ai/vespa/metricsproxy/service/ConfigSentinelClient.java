// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import java.util.logging.Level;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Connects to the config sentinel and gets information like pid for the services on the node
 */
public class ConfigSentinelClient extends AbstractComponent {
    private final static Logger log = Logger.getLogger(ConfigSentinelClient.class.getName());

    private final Supervisor supervisor;

    @Inject
    public ConfigSentinelClient() {
        supervisor = new Supervisor(new Transport("sentinel-client")).useSmallBuffers();
    }

    @Override
    public void deconstruct() {
        supervisor.transport().shutdown().join();
        super.deconstruct();
    }

    /**
     * Update all services reading from config sentinel
     *
     * @param services The list of services
     */
    synchronized void updateServiceStatuses(List<VespaService> services) {
        try {
            setStatus(services);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to update service pids from sentinel", e);
        }
    }


    /**
     * Update status
     *
     * @param s The service to update the status for
     */
    synchronized void ping(VespaService s) {
        List<VespaService> services = new ArrayList<>();
        services.add(s);
        log.log(Level.FINE, "Ping for service " + s);
        try {
            setStatus(services);
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to update service pids from sentinel", e);
        }
    }

    /**
     * Update the status (pid check etc)
     *
     * @param services list of services
     * @throws Exception if something went wrong
     */
    protected synchronized void setStatus(List<VespaService> services) throws Exception {
        String in = sentinelLs();
        BufferedReader reader = new BufferedReader(new StringReader(in));
        String line;
        List<VespaService> updatedServices = new ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if (line.equals("")) {
                break;
            }

            VespaService s = parseServiceString(line, services);
            if (s != null) {
                updatedServices.add(s);
            }
        }

        //Check if there are services that were not found in output
        //from the sentinel
        for (VespaService s : services) {
            if ((!s.getServiceName().equals("configserver")) && !updatedServices.contains(s)) {
                log.log(Level.FINE,"Service " + s +  " is no longer found with sentinel - setting alive = false");
                s.setAlive(false);
            }
        }

        //Close streams
        reader.close();
    }

    static VespaService parseServiceString(String line, List<VespaService> services) {
        String[] parts = line.split(" ");
        if (parts.length < 3)
            return null;

        String name = parts[0];
        int pid = -1;
        String state = null;
        VespaService service = null;

        for (VespaService s : services) {
            if (s.getInstanceName().compareToIgnoreCase(name) == 0) {
                service = s;
                break;
            }
        }

        //Could not find this service
        //nothing wrong with that as the check is invoked per line from sentinel
        if (service == null) {
            return service;
        }

        for (int i = 1; i < parts.length; i++) {
            String keyValue[] = parts[i].split("=");

            String key = keyValue[0];
            String value = keyValue[1];

            if (key.equals("state")) {
                state = value;
            } else if (key.equals("pid")) {
                pid = Integer.parseInt(value);
            }
        }

        if (state != null) {
            service.setState(state);
            if (pid >= 0 && "RUNNING".equals(state)) {
                service.setAlive(true);
                service.setPid(pid);
            } else {
                service.setAlive(false);

            }
        } else {
            service.setAlive(false);
        }
        return service;
    }

    String sentinelLs() {
        String servicelist = "";
        int rpcPort = 19097;
        Spec spec = new Spec("localhost", rpcPort);
        Target connection = supervisor.connect(spec);
        try {
            if (connection.isValid()) {
                Request req = new Request("sentinel.ls");
                connection.invokeSync(req, 5.0);
                if (req.errorCode() == ErrorCode.NONE &&
                    req.checkReturnTypes("s"))
                {
                    servicelist = req.returnValues().get(0).asString();
                } else {
                    log.log(Level.WARNING, "Bad answer to RPC request: " + req.errorMessage());
                }
            } else {
                log.log(Level.WARNING, "Could not connect to sentinel at: "+spec);
            }
            return servicelist;
        } finally {
            connection.close();
        }
    }
}
