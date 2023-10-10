// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.jrt.*;

public class RPCServer {

    public static void main(String[] args) {
	if (args.length != 1) {
	    System.err.println("usage: RPCServer <spec>");
	    System.exit(1);
	}
	Supervisor orb = new Supervisor(new Transport());
	try {
	    orb.listen(new Spec(args[0]));
	} catch (ListenFailedException e) {
	    System.err.println("could not listen at " + args[0]);
	    System.exit(1);
	}
	orb.transport().join();
    }
}
