#!/bin/sh
# Use this script to generate config files based on test.sd:
# ./generate.sh

# Generate config files:
cd $HOME/git/vespa
WORK_DIR=$HOME/git/vespa/streamingvisitors/src/tests/searchvisitor/cfg
mvn test -Dtest=SchemaToDerivedConfigExporter -Dschema.exporter.path=$WORK_DIR -pl config-model

cd $WORK_DIR
# Delete files not relevant for streaming:
rm attributes.cfg
rm ilscripts.cfg
rm imported-fields.cfg
rm index-info.cfg
rm indexschema.cfg
rm onnx-models.cfg
rm schema-info.cfg

# Add search cluster name as part of file name:
mv juniperrc.cfg juniperrc.mycl.cfg
mv rank-profiles.cfg rank-profiles.mycl.cfg
mv summary.cfg summary.mycl.cfg
mv vsmfields.cfg vsmfields.mycl.cfg
mv vsmsummary.cfg vsmsummary.mycl.cfg

# Add newline at eof
for file in *.cfg; do echo >> $file; done
