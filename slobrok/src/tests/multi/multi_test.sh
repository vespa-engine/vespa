#/bin/bash
./start.sh
sleep 2
./slobrok_multi_test_app || (./stop.sh; false)
./stop.sh
