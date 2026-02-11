// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.jrt.*;

public class SimpleServer {

    public void rpc_inc(Request req) {
	req.returnValues().add(new Int32Value(req.parameters().get(0).asInt32()
					      + 1));
    }

    public void rpc_echo(Request req) {
	for (int i = 0; i < req.parameters().size(); i++) {
	    req.returnValues().add(req.parameters().get(i));
	}
    }

    public void rpc_test(Request req) {
	int value = req.parameters().get(0).asInt32();
	int error = req.parameters().get(1).asInt32();
	int extra = req.parameters().get(2).asInt8();

	req.returnValues().add(new Int32Value(value));
	if (extra != 0) {
	    req.returnValues().add(new Int32Value(value));	
	}
	if (error != 0) {
	    req.setError(error, "Custom error");
	}
    }

    public static void main(String[] args) {
	if (args.length != 1) {
	    System.err.println("usage: SimpleServer <spec>");
	    System.exit(1);
	}
	Supervisor orb = new Supervisor(new Transport());
	SimpleServer handler = new SimpleServer();
	orb.addMethod(new Method("inc", "i", "i", handler::rpc_inc));
	orb.addMethod(new Method("echo", "*", "*", handler::rpc_echo));
	orb.addMethod(new Method("test", "iib", "i", handler::rpc_test));
	try {
	    orb.listen(new Spec(args[0]));
	} catch (ListenFailedException e) {
	    System.err.println("could not listen at " + args[0]);
	    System.exit(1);	
	}
	orb.transport().join();
    }
}
