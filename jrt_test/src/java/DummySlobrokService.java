// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import com.yahoo.jrt.*;
import com.yahoo.jrt.slobrok.api.*;

public class DummySlobrokService {
    public static void main(String args[]) {
	if (args.length < 3) {
	    System.err.println("Usage: DummySlobrokService <myspec> "
			       + "<slobrokspec> <service> [service] ...");
	    System.exit(1);
	}
	Spec mySpec          = new Spec(args[0]);
	String[] slobroks    = new String[1];
	slobroks[0]          = args[1];
        SlobrokList slist    = new SlobrokList();
        slist.setup(slobroks);
	int serviceCnt       = args.length - 2;
	String[] serviceList = new String[serviceCnt];
	for (int i = 0; i < serviceCnt; i++) {
	    serviceList[i] = args[i + 2];
	}
	Supervisor orb = new Supervisor(new Transport());
	Spec listenSpec = new Spec(mySpec.port());
	try {
	    Acceptor acceptor = orb.listen(listenSpec);
            System.out.println("Listening at " + listenSpec);
	    Register reg = new Register(orb, slist,
					mySpec.host(), mySpec.port());
	    for (int i = 0; i < serviceList.length; i++) {
		System.out.println("trying to register " + serviceList[i]);
		reg.registerName(serviceList[i]);
	    }
	    orb.transport().join();
	    acceptor.shutdown().join();
	} catch (ListenFailedException e) {
	    System.err.println("Could not listen at " + listenSpec);
	    orb.transport().shutdown().join();
	}
    }
}
