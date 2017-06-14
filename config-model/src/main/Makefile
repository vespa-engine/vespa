# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#all: services.xsd hosts.xsd

trangjar=../../target/trang.jar

all: resources/schema/services.rng resources/schema/hosts.rng resources/schema/container-include.rng resources/schema/services.xsd resources/schema/hosts.xsd resources/schema/container-include.xsd resources/schema/deployment.xsd

resources/schema/services.rng: resources/schema/services.rnc resources/schema/common.rnc resources/schema/admin.rnc resources/schema/clients.rnc resources/schema/docproc.rnc resources/schema/routing.rnc resources/schema/clients-v2.rnc resources/schema/content.rnc resources/schema/genericmodule.rnc resources/schema/legacygenericcluster.rnc resources/schema/genericcluster.rnc resources/schema/legacygenericmodule.rnc resources/schema/containercluster.rnc
	java -jar $(trangjar) -I rnc -O rng resources/schema/services.rnc resources/schema/services.rng

resources/schema/services.xsd: resources/schema/services.rng
	java -jar $(trangjar) -I rng -O xsd resources/schema/services.rng resources/schema/services.xsd


resources/schema/container-include.rng: resources/schema/common.rnc resources/schema/containercluster.rnc
	java -jar $(trangjar) -I rnc -O rng resources/schema/container-include.rnc resources/schema/container-include.rng

resources/schema/container-include.xsd: resources/schema/container-include.rng
	java -jar $(trangjar) -I rng -O xsd resources/schema/container-include.rng resources/schema/container-include.xsd


resources/schema/hosts.rng: resources/schema/hosts.rnc
	java -jar $(trangjar) -I rnc -O rng resources/schema/hosts.rnc resources/schema/hosts.rng

resources/schema/hosts.xsd: resources/schema/hosts.rng
	java -jar $(trangjar) -I rng -O xsd resources/schema/hosts.rng resources/schema/hosts.xsd

resources/schema/deployment.rng: resources/schema/deployment.rnc
	java -jar $(trangjar) -I rnc -O rng resources/schema/deployment.rnc resources/schema/deployment.rng

resources/schema/deployment.xsd: resources/schema/deployment.rng
	java -jar $(trangjar) -I rng -O xsd resources/schema/deployment.rng resources/schema/deployment.xsd

clean:
	rm -f resources/schema/*.rng
	rm -f resources/schema/*.xsd
