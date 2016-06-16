#/bin/bash
ok=true
./start.sh
./slobrok_multi_test_app || ok=false
./stop.sh
$ok
