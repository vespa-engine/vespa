// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.jrt.*;

public class Test implements FatalErrorHandler {

    boolean error = false;
    int     port  = 0;

    public void handleFailure(Throwable t, Object o) {
	System.err.println("FATAL ERROR -> " + o);
	t.printStackTrace();
	error = true;
    }

    public void myMain() {
	Supervisor server = new Supervisor(new Transport(this));
	Supervisor client = new Supervisor(new Transport(this));
	try {
	    port = server.listen(new Spec(0)).port(); // random port
	} catch (ListenFailedException e) {
	    System.err.println("Listen failed");
	    System.exit(1);
	}

	Target a = client.connect(new Spec("localhost", port));

	server.transport().shutdown().join();
	client.transport().shutdown().join();

	if (error) {
	    System.exit(1);
	}
    }

    public static void main(String[] args) {
	new Test().myMain();
    }
}
