#!/bin/bash

numdocs=500000
stripe_bits=8

base_cmd="numactl --cpunodebind=0 --localalloc perf stat -ddd env LD_PRELOAD=$HOME/vespa/lib64/vespa/malloc/libvespamalloc.so ./vespa-feed-bm --documents $numdocs --put-passes 1 --update-passes 10 --remove-passes 0 --indexing-sequencer throughput"

spi_only="$base_cmd --max-pending 8000 --client-threads 1"
base_for_rest="$base_cmd --max-pending 2000 --client-threads 2 --response-threads 3"

chain_base="$base_for_rest --use-storage-chain"
chain_stripe="$chain_base --bucket-db-stripe-bits $stripe_bits"
chain_stripe_async="$chain_stripe --use-async-message-handling"
service_layer="$base_for_rest --enable-service-layer --bucket-db-stripe-bits $stripe_bits --use-async-message-handling"
service_layer_rpc="$service_layer --rpc-network-threads 3 --rpc-targets-per-node 14"
service_layer_mbus="$service_layer --use-message-bus"
distributor_chain="$service_layer_rpc --enable-distributor --use-storage-chain"
distributor="$service_layer_rpc --enable-distributor"

echo "Running test: spi_only"
$spi_only
echo "Running test: chain_base"
$chain_base
echo "Running test: chain_stripe"
$chain_stripe
echo "Running test: chain_stripe_async"
$chain_stripe_async
echo "Running test: service_layer_rpc"
$service_layer_rpc
echo "Running test: service_layer_mbus"
$service_layer_mbus
echo "Running test: distributor_chain"
$distributor_chain
echo "Running test: distributor"
$distributor
