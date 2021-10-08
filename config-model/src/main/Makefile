# Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

srcdir=resources/schema
outputdir=../../target/generated-sources/trang/resources/schema

trangjar=../../target/trang.jar

all: ${outputdir} ${outputdir}/services.rng ${outputdir}/hosts.rng ${outputdir}/container-include.rng ${outputdir}/services.xsd ${outputdir}/hosts.xsd ${outputdir}/container-include.xsd ${outputdir}/deployment.xsd

${outputdir}:
	mkdir -p ${outputdir}

${outputdir}/services.rng: ${srcdir}/services.rnc ${srcdir}/common.rnc ${srcdir}/admin.rnc ${srcdir}/clients.rnc ${srcdir}/docproc.rnc ${srcdir}/routing.rnc ${srcdir}/clients-v2.rnc ${srcdir}/content.rnc ${srcdir}/genericmodule.rnc ${srcdir}/legacygenericcluster.rnc ${srcdir}/genericcluster.rnc ${srcdir}/legacygenericmodule.rnc ${srcdir}/containercluster.rnc
	java -jar $(trangjar) -I rnc -O rng ${srcdir}/services.rnc ${outputdir}/services.rng

${outputdir}/services.xsd: ${outputdir}/services.rng
	java -jar $(trangjar) -I rng -O xsd ${outputdir}/services.rng ${outputdir}/services.xsd


${outputdir}/container-include.rng: ${srcdir}/common.rnc ${srcdir}/containercluster.rnc
	java -jar $(trangjar) -I rnc -O rng ${srcdir}/container-include.rnc ${outputdir}/container-include.rng

${outputdir}/container-include.xsd: ${outputdir}/container-include.rng
	java -jar $(trangjar) -I rng -O xsd ${outputdir}/container-include.rng ${outputdir}/container-include.xsd


${outputdir}/hosts.rng: ${srcdir}/hosts.rnc
	java -jar $(trangjar) -I rnc -O rng ${srcdir}/hosts.rnc ${outputdir}/hosts.rng

${outputdir}/hosts.xsd: ${outputdir}/hosts.rng
	java -jar $(trangjar) -I rng -O xsd ${outputdir}/hosts.rng ${outputdir}/hosts.xsd

${outputdir}/deployment.rng: ${srcdir}/deployment.rnc
	java -jar $(trangjar) -I rnc -O rng ${srcdir}/deployment.rnc ${outputdir}/deployment.rng

${outputdir}/deployment.xsd: ${outputdir}/deployment.rng
	java -jar $(trangjar) -I rng -O xsd ${outputdir}/deployment.rng ${outputdir}/deployment.xsd

${outputdir}/validation-overrides.rng: ${srcdir}/validation-overrides.rnc
	java -jar $(trangjar) -I rnc -O rng ${srcdir}/validation-overrides.rnc ${outputdir}/validation-overrides.rng

${outputdir}/deployment.xsd: ${outputdir}/validation-overrides.rng
	java -jar $(trangjar) -I rng -O xsd ${outputdir}/validation-overrides.rng ${outputdir}/validation-overrides.xsd

clean:
	rm -rf ${outputdir}
