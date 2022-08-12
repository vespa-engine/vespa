// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.tool;

import com.yahoo.jrt.DoubleValue;
import com.yahoo.jrt.FloatValue;
import com.yahoo.jrt.Int16Value;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Int64Value;
import com.yahoo.jrt.Int8Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.Value;
import com.yahoo.jrt.Values;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

/**
 * A generic rpc invoker for use by command line tools
 *
 * @author  bratseth
 */
public class RpcInvoker {

    private Value getArgument(Request request, String parameter) {
        if (parameter.length() <= 1 || parameter.charAt(1) != ':')
            return new StringValue(parameter);

        String value = parameter.substring(2);
        switch (parameter.charAt(0)) {
            case 'b':
                return new Int8Value(Byte.parseByte(value));
            case 'h':
                return new Int16Value(Short.parseShort(value));
            case 'i':
                return new Int32Value(Integer.parseInt(value));
            case 'l':
                return new Int64Value(Long.parseLong(value));
            case 'f':
                return new FloatValue(Float.parseFloat(value));
            case 'd':
                return new DoubleValue(Double.parseDouble(value));
            case 's':
                return new StringValue(value);
        }

        throw new IllegalArgumentException("The first letter in '" + parameter + "' must be a type argument. " +
                                           "There is no jrt type identified by '" + parameter.charAt(0) + "'");
    }

    protected Request createRequest(String method, List<String> arguments) {
        Request request = new Request(method);
        if (arguments != null) {
            for (String argument : arguments)
                request.parameters().add(getArgument(request,argument));
        }
        return request;
    }

    /**
     * Invokes a rpc method without throwing an exception
     *
     * @param connectspec the rpc server connection spec
     * @param method the name of the method to invoke
     * @param arguments the argument to the method, or null or an empty list if there are no arguments
     */
    public void invoke(String connectspec, String method, List<String> arguments) {
        Supervisor supervisor = null;
        Target target = null;

        try {
            if (connectspec.indexOf('/') < 0)
                connectspec = "tcp/" + connectspec;

            supervisor = new Supervisor(new Transport("invoker"));
            target = supervisor.connect(new Spec(connectspec));
            Request request = createRequest(method,arguments);
            target.invokeSync(request, Duration.ofSeconds(10));
            if (request.isError()) {
                System.err.println("error(" + request.errorCode() + "): " + request.errorMessage());
                return;
            }
            Values returned = request.returnValues();
            for (int i = 0; i < returned.size(); i++) {
                System.out.println(returned.get(i));
            }
        }
        finally {
            if (target != null)
                target.close();
            if (supervisor != null)
                supervisor.transport().shutdown().join();
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("usage: invoke [-h <connectspec>] <method> [arguments]");
            System.err.println("    Connectspec: This is on the form hostname:port, or tcp/hostname:port");
            System.err.println("                 if omitted, localhost:8086 is used");
            System.err.println("    Arguments: each argument must be a string or on the form <type>:<value>");
            System.err.println("    supported types: {'b','h','i','l','f','d','s'}");
            System.exit(0);
        }
        List<String> arguments = new ArrayList<String>(Arrays.asList(args));
        String connectSpec = "localhost:8086";
        if ("-h".equals(arguments.get(0)) && arguments.size() >= 3) {
            arguments.remove(0);             // Consume -h
            connectSpec = arguments.remove(0);
        }
        String method = arguments.remove(0);
        new RpcInvoker().invoke(connectSpec, method, arguments);
    }

}
