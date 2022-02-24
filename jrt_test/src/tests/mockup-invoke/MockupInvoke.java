// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.jrt.*;

public class MockupInvoke {

    public static void main(String[] args) {
	if (args.length != 3) {
	    System.out.println("error: Wrong number of parameters");
	    System.exit(0);	
	}
	Transport transport = new Transport();
	Supervisor orb = new Supervisor(transport);
	Spec spec = new Spec(args[0]);
	Request req = new Request("concat");
	req.parameters().add(new StringValue(args[1]));
	req.parameters().add(new StringValue(args[2]));
        Target target = orb.connect(spec);
        try {
            target.invokeSync(req, 60.0);
        } finally {
            target.close();
        }
        if (req.isError()) {
	    System.out.println("error: " + req.errorCode()
			       + ": " + req.errorMessage());
	    System.exit(0);
	}
	if (req.returnValues().size() != 1
	    || req.returnValues().get(0).type() != 's') {

	    System.out.println("error: Wrong return values");
	    System.exit(0);	
	}
	System.out.println("result: '"
			   + req.returnValues().get(0).asString()
			   + "'");
    }
}
