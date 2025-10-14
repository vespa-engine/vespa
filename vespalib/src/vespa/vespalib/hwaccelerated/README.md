# Testing scalable Highway AArch64 SVE/SVE2 vector kernels

At the time of writing there is no hardware available for SVE2 with wider
vector registers than 128 bits. This makes it tricky to ensure that vector
kernels using `ScalableTag` will work in practice on future hardware.

To get around this, QEMU user mode emulation can be used:

```shell
qemu-aarch64 -cpu max,sve-default-vector-length=$n ./vespalib_hwaccelerated_test_app
```
Where `$n` is the vector width _in bytes_ and should preferably be tested
for all of `16, 32, 64, 128, 256`. SVE/SVE2 tops out at 2048 bits (256 bytes),
so no need to test anything larger.

Example output:
```
$ qemu-aarch64 -cpu max,sve-default-vector-length=64 ./vespalib_hwaccelerated_test_app
[==========] Running 2 tests from 1 test suite.
[----------] Global test environment set-up.
[----------] 2 tests from HwAcceleratedTest
Testing accelerators:
AutoVec - NEON
AutoVec - NEON_FP16_DOTPROD
Highway - SVE2 (512 bit vector width)
Highway - SVE (512 bit vector width)
Highway - NEON_BF16 (128 bit vector width)
Highway - NEON (128 bit vector width)
[ RUN      ] HwAcceleratedTest.euclidean_distance_impls_match_source_of_truth
[       OK ] HwAcceleratedTest.euclidean_distance_impls_match_source_of_truth (3079 ms)
[ RUN      ] HwAcceleratedTest.dot_product_impls_match_source_of_truth
[       OK ] HwAcceleratedTest.dot_product_impls_match_source_of_truth (3651 ms)
[----------] 2 tests from HwAcceleratedTest (6731 ms total)
```