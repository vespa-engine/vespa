// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <util/filereader.h>
#include <string.h>
#include <cassert>

int
main(int argc, char **argv)
{
  (void) argc;
  (void) argv;

  // write test file with messy newlines.
  std::ofstream file("filereader_messy.txt", std::ofstream::out | std::ofstream::binary | std::ofstream::trunc);
  if (!file) {
    printf("can't open 'filereader_messy.txt' for writing!\n");
    return -1;
  }
  const char *l1  = "a line with only newline\n";
  const char *l2  = "a line with only return\r";
  const char *l3  = "a line with newline return\n\r";
  const char *l4  = "a line with return newline\r\n";
  const char *l5  = "2 empty lines with newline\n";
  const char *l6  = "\n";
  const char *l7  = "\n";
  const char *l8  = "2 empty lines with return\r";
  const char *l9  = "\r";
  const char *l10 = "\r";
  const char *l11 = "2 empty lines with newline return\n\r";
  const char *l12 = "\n\r";
  const char *l13 = "\n\r";
  const char *l14 = "2 empty lines with return newline\r\n";
  const char *l15 = "\r\n";
  const char *l16 = "\r\n";
  const char *l17 = "file ends here x";
  file.write(l1, strlen(l1));
  file.write(l2, strlen(l2));
  file.write(l3, strlen(l3));
  file.write(l4, strlen(l4));
  file.write(l5, strlen(l5));
  file.write(l6, strlen(l6));
  file.write(l7, strlen(l7));
  file.write(l8, strlen(l8));
  file.write(l9, strlen(l9));
  file.write(l10, strlen(l10));
  file.write(l11, strlen(l11));
  file.write(l12, strlen(l12));
  file.write(l13, strlen(l13));
  file.write(l14, strlen(l14));
  file.write(l15, strlen(l15));
  file.write(l16, strlen(l16));
  file.write(l17, strlen(l17));
  file.close();

  // convert file to use only '\n' as newlines.
  FileReader *reader = new FileReader();
  if (!reader->Open("filereader_messy.txt")) {
    printf("can't open 'filereader_messy.txt' for reading!\n");
    delete reader;
    return -1;
  }
  file = std::ofstream("filereader_clean.txt", std::ofstream::out | std::ofstream::binary | std::ofstream::trunc);
  if (!file) {
    printf("can't open 'filereader_clean.txt' for writing!\n");
    reader->Close();
    delete reader;
    return -1;
  }
  int res;
  int buflen = 10240;
  char buf[buflen];
  while ((res = reader->ReadLine(buf, buflen - 1)) >= 0) {
    // printf("len=%d, content:>%s<\n", res, buf);
    buf[res] = '\n';
    buf[res + 1] = '\0';
    file.write(buf, strlen(buf));
  }
  file.close();
  reader->Close();
  delete reader;

  printf("Please confirm that 'filereader_clean.txt' is equal to\n");
  printf("'filereader_messy.txt' except that all line separators have\n");
  printf("been replaced by a single '\\n' character (hex 0a).\n");
  FileReader verify;
  assert(verify.Open("filereader_messy.txt"));
  assert(verify.ReadLine(buf, buflen - 1) == ssize_t(strlen(l1)-1));
  assert(memcmp(l1, buf, strlen(l1)-1) == 0);
  assert(verify.ReadLine(buf, buflen - 1) == ssize_t(strlen(l2)-1));
  assert(memcmp(l2, buf, strlen(l2)-1) == 0);
  while ((res = verify.ReadLine(buf, buflen - 1)) >= 0) {
     printf("len=%d, content:>%s<\n", res, buf);
  }
  verify.Reset();
  assert(verify.ReadLine(buf, buflen - 1) == ssize_t(strlen(l1)-1));
  assert(memcmp(l1, buf, strlen(l1)-1) == 0);
}
