// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/util/cpu.h>

namespace vespalib {

#if defined(__i386__) || defined(__x86_64)

X86CpuInfo::CpuFeature X86CpuInfo::_CpuFeatureList[64] =
{
  {  0,     "FPU", "Floating-point unit on-Chip",            "The processor contains an FPU that supports the Intel387 floating-point instruction set." },
  {  1,     "VME", "Virtual Mode Extension",                 "The processor supports extensions to virtual-8086 mode." },
  {  2,      "DE", "Debugging Extension",                    "The processor supports I/O breakpoints, including the CR4.DE bit for enabling debug extensions and optional trapping of access to the DR4 and DR5 registers." },
  {  3,     "PSE", "Page Size Extension",                    "The processor supports 4-Mbyte pages." },
  {  4,     "TSC", "Time Stamp Counter",                     "The RDTSC instruction is supported including the CR4.TSD bit for access/privilege control." },
  {  5,     "MSR", "Model Specific Registers",               "Model Specific Registers are implemented with the RDMSR, WRMSR instructions." },
  {  6,     "PAE", "Physical Address Extension",             "Physical addresses greater than 32 bits are supported." },
  {  7,     "MCE", "Machine Check Exception",                "Machine Check Exception, Exception 18, and the CR4.MCE enable bit are supported" },
  {  8,     "CX8", "CMPXCHG8 Instruction Supported",         "The compare and exchange 8 bytes instruction is supported." },
  {  9,    "APIC", "On-chip APIC Hardware Supported",        "The processor contains a software-accessible Local APIC." },
  { 10,     "RES", "Reserved",                               "Do not count on their value." },
  { 11,     "SEP", "Fast System Call",                       "Indicates whether the processor supports the Fast System Call instructions, SYSENTER and SYSEXIT. NOTE: Refer to Section 3.4 for further information regarding SYSENTER/ SYSEXIT feature and SEP feature bit." },
  { 12,    "MTRR", "Memory Type Range Registers",            "The Processor supports the Memory Type Range Registers specifically the MTRR_CAP register." },
  { 13,     "PGE", "Page Global Enable",                     "The global bit in the page directory entries (PDEs) and page table entries (PTEs) is supported, indicating TLB entries that are common to different processes and need not be flushed. The CR4.PGE bit controls this feature." },
  { 14,     "MCA", "Machine Check Architecture",             "The Machine Check Architecture is supported, specifically the MCG_CAP register." },
  { 15,    "CMOV", "Conditional Move Instruction Supported", "The processor supports CMOVcc, and if the FPU feature flag (bit 0) is also set, supports the FCMOVCC and FCOMI instructions." },
  { 16,     "PAT", "Page Attribute Table",                   "Indicates whether the processor supports the Page Attribute Table. This feature augments the Memory Type Range Registers (MTRRs), allowing an operating system to specify attributes of memory on 4K granularity through a linear address." },
  { 17,  "PSE-36", "36-bit Page Size Extension",             "Indicates whether the processor supports 4-Mbyte pages that are capable of addressing physical memory beyond 4GB. This feature indicates that the upper four bits of the physical address of the 4-Mbyte page is encoded by bits 13-16 of the page directory entry." },
  { 18,     "PSN", "Processor serial number is present and enabled", "The processor supports the 96-bit processor serial number feature, and the feature is enabled." },
  { 19,   "CLFSH", "CLFLUSH Instruction supported",          "Indicates that the processor supports the CLFLUSH instruction." },
  { 20,     "RES", "Reserved",                               "Do not count on their value." },
  { 21,      "DS", "Debug Store",                            "Indicates that the processor has the ability to write a history of the branch to and from addresses into a memory buffer." },
  { 22,    "ACPI", "Thermal Monitor and Software Controlled Clock Facilities supported", "The processor implements internal MSRs that allow processor temperature to be monitored and processor performance to be modulated in predefined duty cycles under software control." },
  { 23,     "MMX", "Intel Architecture MMX technology supported", "The processor supports the MMX technology instruction set extensions to Intel Architecture." },
  { 24,    "FXSR", "Fast floating point save and restore",   "Indicates whether the processor supports the FXSAVE and FXRSTOR instructions for fast save and restore of the floating point context. Presence of this bit also indicates that CR4.OSFXSR is available for an operating system to indicate that it uses the fast save/restore instructions." },
  { 25,     "SSE", "Streaming SIMD Extensions supported",    "The processor supports the Streaming SIMD Extensions to the Intel Architecture." },
  { 26,    "SSE2", "Streaming SIMD Extensions 2",            "Indicates the processor supports the Streaming SIMD Extensions - 2 Instructions." },
  { 27,      "SS", "Self-Snoop",                             "The processor supports the management of conflicting memory types by performing a snoop of its own cache structure for transactions issued to the bus." },
  { 28,     "HTT", "Hyper-Threading Technology",             "The processor supports Hyper-Threading Technology." },
  { 29,      "TM", "Thermal Monitor supported",              "The processor implements the Thermal Monitor automatic thermal control circuit (TCC)." },
  { 30,    "IA64", "IA64 Capabilities",                      "The processor is a member of the Intel Itanium processor family and currently operating in IA32 emulation mode." },
  { 31,     "PBE", "Pending Break Enable",                   "The processor supports the use of the FERR#/PBE# pin when th eprocessor is in the stop-clock state(STPCLK# is asserted) to signal the processor that an interrupt is pending and that the processor should return to normal operation to handle the interrupt. Bit 10 (PBE enable) in the IA32_MISc_ENABLE MSR enables this capability." },

  { 32,    "SSE3", "Streaming SIMD Extensions 3",            "The processor supports the Streaming SIMD Extensions 3 instructions." },
  { 33,     "RES", "Reserved",                               "Do not count on their value." },
  { 34,  "DTES64", "64-Bit Debug Store",                     "Indicates that the processor has the ability to write a history of the 64-bit branch to and from addresses into a memory buffer." },
  { 35, "MONITOR", "MONITOR/MWAIT",                          "The processor supports the MONITOR and MWAIT instructions." },
  { 36,  "DS-CPL", "CPL Qualified Debug Store",              "The processor supports the extensions to the Debug Store feature to allow for branch message storage qualified by CPL." },
  { 37,     "VMX", "Virtual Machine Extensions",             "The processor supports Intel Virtualization Technology." },
  { 38,     "SMX", "Safer Mode Extensions",                  "The processor supports Intel Trusted Execution Technology." },
  { 39,     "EST", "Enhanced Intel SpeedStep",               "The processor supports Enhanced Intel SpeedStep Technology and implements the IA32_PERF_STS and IA32_PERF_CTL registers." },
  { 40,     "TM2", "Thermal Monitor 2",                      "The processor implements the Thermal Monitor 2 thermal control circuit (TCC)." },
  { 41,   "SSSE3", "Supplemental Streaming SIMD Extensions 3", "The processor supports the Supplemental Streaming SIMD Extensions 3 instructions." },
  { 42,     "CID", "L1 Context ID",                          "The L1 data cache mode can be set to either adaptive mode or shared mode by the BIOS." },
  { 43,     "RES", "Reserved",                               "Do not count on their value." },
  { 44,     "RES", "Reserved",                               "Do not count on their value." },
  { 45,    "CX16", "CMPXCHG16B",                             "This processor supports the CMPXCHG16B instruction." },
  { 46,    "xTPR", "Send Task Priority Messages",            "The processor supports the ability to disable sending Task Priority messages. When this feature flag is set, Task Priority messages may be disabled. Bit 23 (Echo TPR disable) in the IA32_MISC_ENABLE MSR controls the sending of Task Priority messages." },
  { 47,    "PDCM", "Perfmon and Debug Capability",           "The processor supports the Performance Capabilities MSR. IA32_PERF_CAPABILITIES register is MSR 345h." },
  { 48,     "RES", "Reserved",                               "Do not count on their value." },
  { 49,     "RES", "Reserved",                               "Do not count on their value." },
  { 50,     "DCA", "Direct Cache Access",                    "The processor supports the ability to prefetch data from a memory mapped device." },
  { 51,  "SSS4.1", "Streaming SIMD Extensions 4.1",          "The processor supports the Streaming SIMD Extensions 4.1 instructions." },
  { 52,  "SSE4.2", "Streaming SIMD Extensions 4.2",          "The processor supports the Streaming SIMD Extensions 4.2 instructions." },
  { 53,  "x2APIC", "Extended xAPIC Support",                 "The processor supports x2APIC feature." },
  { 54,   "MOVBE", "MOVBE Instruction",                      "The processor supports MOVBE instruction." },
  { 55,  "POPCNT", "POPCNT Instruction",                     "The processor supports the POPCNT instruction." },
  { 56,     "RES", "Reserved",                               "Do not count on their value." },
  { 57,     "RES", "Reserved",                               "Do not count on their value." },
  { 58,   "XSAVE", "XSAVE/XSTOR States",                     "The processor supports the XSAVE/XRSTOR processor extended states feature, the XSETBV/ XGETBV instructions, and the XFEATURE_ENABLED_MASK register (XCR0)." },
  { 59,  "OXSAVE", "OS Enabled XSAVE",                       "A value of 1 indicates that the OS has enabled XSETBV/XGETBV instructions to access the XFEATURE_ENABLED_MASK register (XCR0), and support for processor extended state management using XSAVE/XRSTOR." },
  { 60,     "RES", "Reserved",                               "Do not count on their value." },
  { 61,     "RES", "Reserved",                               "Do not count on their value." },
  { 62,     "RES", "Reserved",                               "Do not count on their value." },
  { 63,     "RES", "Reserved",                               "Do not count on their value." }

};

// private empty constructor
X86CpuInfo::X86CpuInfo()
{
    memset(this, 0, sizeof(*this));
}

X86CpuInfo  X86CpuInfo::_CpuInfoSingleton;
X86CpuInfo* X86CpuInfo::_CpuInfoSingletonPtr = 0;

void X86CpuInfo::fillSingleton()
{
    X86CpuInfo tmp;

    uint32_t b, c, d;
    Cpu::cpuid(0, tmp.largestStandardFunction, b, c, d);
    memcpy(tmp.cpuName+0, &b, 4);
    memcpy(tmp.cpuName+4, &d, 4);
    memcpy(tmp.cpuName+8, &c, 4);

    Cpu::cpuid(1, tmp.versionInfo, tmp.apicInfo, tmp.extendedFeatures, tmp.mainFeatures);
    uint64_t cf = tmp.extendedFeatures;
    cf = (cf << 32) | tmp.mainFeatures;
    tmp._cpuFeatures = cf;

    // add more cpuid extraction here

    _CpuInfoSingleton = tmp;
}

void X86CpuInfo::print(FILE * fp)
{
    uint32_t stepping   = (cpuInfo().versionInfo & 0xF);
    uint32_t baseModel  = (cpuInfo().versionInfo >> 4) & 0xF;
    uint32_t baseFamily = (cpuInfo().versionInfo >> 8) & 0xF;
    uint32_t extModel   = (cpuInfo().versionInfo >> 16) & 0xF;
    uint32_t extFamily  = (cpuInfo().versionInfo >> 20) & 0xFF;

    fprintf(fp, "cpuFeatures=%0x, cpuExtendedFeatures=%0x, family %d/%d, model %d/%d, stepping=%d\n\n",
            cpuInfo().mainFeatures, cpuInfo().extendedFeatures,
            baseFamily, extFamily, baseModel, extModel, stepping);
	
    fprintf(fp, "largestStandardFunction=%d, cpuName=%s\n",
            cpuInfo().largestStandardFunction, cpuInfo().cpuName);

    uint64_t cpuFeatures  = cpuInfo()._cpuFeatures;
    for (unsigned int i = 0; i < sizeof(_CpuFeatureList)/sizeof(_CpuFeatureList[0]); i++) {
        const CpuFeature & f = _CpuFeatureList[i];
        if ((cpuFeatures >> i ) & 1) {
            fprintf(fp, "Feature #%d = %s\t%s\n\t%s\n", i, f.Name, f.Description, f.Comment);
        }
    }
}


#endif

}

#if 0
int main(int, char **)
{
    vespalib::X86CpuInfo::print(stdout);
}
#endif
