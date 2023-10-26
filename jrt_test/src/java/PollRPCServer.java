// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.jrt.*;

public class PollRPCServer {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: PollRPCServer <spec>");
            System.exit(1);
        }
	Transport transport = new Transport();
	Supervisor orb = new Supervisor(transport);
	Target target = orb.connect(new Spec(args[0]));
	Request req = new Request("frt.rpc.ping");
	int retry = 0;
	System.out.print("polling '" + args[0] + "' ");
	while (true) {
	    target.invokeSync(req, 60.0);
	    System.out.print(".");
	    if (req.errorCode() != ErrorCode.CONNECTION
		|| ++retry == 500) {
		break;
	    }
	    try { Thread.sleep(250); } catch (Exception e) {}
	    target = orb.connect(new Spec(args[0]));
	    req = new Request("frt.rpc.ping");
	}
	if (req.isError()) {
            System.out.println(" fail");
	    System.exit(1);
	}
	System.out.println(" ok");	
    }
}
