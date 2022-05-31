// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cstdio>
#include <cerrno>
#include <cassert>
#include <cstring>
#include <link.h>
#include <sys/mman.h>

/**
 * This is experimental code that will map code segments in binary and dso into
 * anonymous mappings prefering huge page mappings.
 */
namespace {

constexpr size_t HUGEPAGE_SIZE = 0x200000;

void *
mmap_huge(size_t sz) {
    assert ((sz % HUGEPAGE_SIZE) == 0);
    void * mem = mmap(nullptr, sz, PROT_READ | PROT_WRITE, MAP_ANON | MAP_PRIVATE, -1, 0);
    assert(mem != MAP_FAILED);
    if (madvise(mem, sz, MADV_HUGEPAGE) != 0) {
        fprintf(stderr, "load_as_huge.cpp:mmap_huge = > madvise(%p, %ld, MADV_HUGEPAGE) FAILED, errno= %d = %s\n", mem, sz, errno, strerror(errno));
    }
    return mem;
}

size_t round_huge_down(size_t v) { return v & ~(HUGEPAGE_SIZE - 1); }
size_t round_huge_up(size_t v) { return round_huge_down(v + (HUGEPAGE_SIZE - 1)); }

#ifdef __clang__
void
non_optimized_non_inlined_memcpy(void *dest_in, const void *src_in, size_t n) __attribute__((noinline, optnone)) ;
#else
void
non_optimized_non_inlined_memcpy(void *dest_in, const void *src_in, size_t n) __attribute__((noinline, optimize(1))) ;
#endif

// Simple memcpy replacement to avoid calling code in other dso.
void
non_optimized_non_inlined_memcpy(void *dest_in, const void *src_in, size_t n) {
    char *dest = static_cast<char *>(dest_in);
    const char *src = static_cast<const char *>(src_in);
    for (size_t i(0); i < n ; i++) {
        dest[i] = src[i];
    }
}

/**
 * Make a large mapping if code is larger than HUGEPAGE_SIZE and copies the content of the various segments.
 * Then remaps the areas back to its original location.
 */
bool
remap_segments(size_t base_vaddr, const Elf64_Phdr * segments, size_t count) {
    assert(count > 0);
    const Elf64_Phdr & first = segments[0];
    const Elf64_Phdr & last = segments[count - 1];
    size_t start_vaddr = base_vaddr + first.p_vaddr;
    size_t end_vaddr = base_vaddr + last.p_vaddr + last.p_memsz;
    if (end_vaddr - start_vaddr < HUGEPAGE_SIZE) {
        return false;
    }

    size_t huge_start = round_huge_down(start_vaddr);
    size_t huge_end = round_huge_up(end_vaddr);
    size_t huge_size = huge_end - huge_start;
    char * new_huge = static_cast<char *>(mmap_huge(huge_size));
    char * new_huge_end = new_huge + huge_size;
    char * last_end = new_huge;
    for (size_t i(0); i < count; i++) {
        size_t vaddr = base_vaddr + segments[i].p_vaddr;
        size_t huge_offset = vaddr - huge_start;
        char * dest = new_huge + huge_offset;
        assert(dest >= last_end);
        if (dest > last_end) {
            int munmap_retval = munmap(last_end, dest - last_end);
            assert(munmap_retval == 0);
        }
        size_t sz = segments[i].p_memsz;
        last_end = dest + sz;

        if (madvise(dest, sz, MADV_HUGEPAGE) != 0) {
            fprintf(stderr, "load_as_huge.cpp:remap_segments => madvise(%p, %ld, MADV_HUGEPAGE) FAILED, errno= %d = %s\n", dest, sz, errno, strerror(errno));
        }
        non_optimized_non_inlined_memcpy(dest, reinterpret_cast<void*>(vaddr), sz);
        int prot = PROT_READ;
        if (segments[i].p_flags & PF_X) prot|= PROT_EXEC;
        if (segments[i].p_flags & PF_W) prot|= PROT_WRITE;
        int mprotect_retval = mprotect(dest, sz, prot);
        if (mprotect_retval != 0) {
            fprintf(stderr, "mprotect(%p, %ld, %x) FAILED = %d, errno= %d = %s\n", dest, sz, prot, mprotect_retval, errno, strerror(errno));
        }
        void * remapped = mremap(dest, sz, sz, MREMAP_FIXED | MREMAP_MAYMOVE, vaddr);
        assert(remapped != MAP_FAILED);
        assert(remapped == reinterpret_cast<void *>(vaddr));
        fprintf(stdout, "remapped dest=%p, size=%lu to %p\n", dest, sz, remapped);
    }
    assert(new_huge_end >= last_end);
    if (new_huge_end > last_end) {
        int munmap_retval = munmap(last_end, new_huge_end - last_end);
        assert(munmap_retval);
    }
    return true;
}

int
remapElfHeader(struct dl_phdr_info *info, size_t info_size, void *data) {
    (void) info_size;
    (void) data;
    fprintf(stdout, "processing elf header '%s' with %d entries, start=%lx\n",
            info->dlpi_name, info->dlpi_phnum, info->dlpi_addr);
    for (int i = 0; i < info->dlpi_phnum; i++) {
        const Elf64_Phdr &phdr = info->dlpi_phdr[i];
        //fprintf(stdout, "p_vaddr=%lx p_paddr=%lx, p_offset=%lx p_filesz=%lx, p_memsz=%lx, allign=%lu type=%d flags=%x\n",
        //    phdr.p_vaddr, phdr.p_paddr, phdr.p_offset, phdr.p_filesz, phdr.p_memsz, phdr.p_align, phdr.p_type, phdr.p_flags);
        if ((phdr.p_type == PT_LOAD) && (phdr.p_flags == (PF_R | PF_X))) {
            //void *vaddr = reinterpret_cast<void *>(info->dlpi_addr + phdr.p_vaddr);
            //uint64_t size = phdr.p_filesz;
            //fprintf(stdout, "LOAD_RX: vaddr=%lx p_filesz=%lu, p_memsz=%lu\n", phdr.p_vaddr, phdr.p_filesz, phdr.p_memsz);
            remap_segments(info->dlpi_addr, &phdr, 1);
        }
    }
    return 0;
}

}

extern "C" int remapTextWithHugePages();

int
remapTextWithHugePages() {
    int retval = dl_iterate_phdr(remapElfHeader, nullptr);
    fprintf(stdout, "dl_iterate_phdr() = %d\n", retval);
    return retval;
}

static long num_huge_code_pages = remapTextWithHugePages();
