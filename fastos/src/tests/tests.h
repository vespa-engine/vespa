// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/app.h>
#include <vespa/fastos/socket.h>
#include <vespa/fastos/thread.h>
#include <cstring>

class BaseTest : public FastOS_Application
{
private:
   BaseTest(const BaseTest&);
   BaseTest &operator=(const BaseTest&);

   int totallen;
   bool _allOkFlag;
public:
   const char *okString;
   const char *failString;

   BaseTest ()
     : totallen(70),
       _allOkFlag(true),
       okString("SUCCESS"),
       failString("FAILURE")
   {
   }

   virtual ~BaseTest() {};

   bool allWasOk() const { return _allOkFlag; }

   void PrintSeparator ()
   {
      for(int i=0; i<totallen; i++) printf("-");
      printf("\n");
   }

   virtual void PrintProgress (char *string)
   {
      printf("%s", string);
   }
#define MAX_STR_LEN 3000
   bool Progress (bool result, const char *str)
   {
      char string[MAX_STR_LEN];
      snprintf(string, sizeof(string), "%s: %s\n",
         result ? okString : failString, str);
      PrintProgress(string);
      if (! result) { _allOkFlag = false; }
      return result;
   }

   bool Progress (bool result, const char *str, int d1)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, d1);
      return Progress(result, string);
   }

   bool Progress (bool result, const char *str, int d1, int d2)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, d1, d2);
      return Progress(result, string);
   }

   bool Progress (bool result, const char *str, const char *s1)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, s1);
      return Progress(result, string);
   }

   bool Progress (bool result, const char *str, const FastOS_ThreadInterface *s1)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, s1);
      return Progress(result, string);
   }

   bool Progress (bool result, const char *str, const FastOS_Socket *s1)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, s1);
      return Progress(result, string);
   }

   bool Progress (bool result, const char *str, const char *s1, const char *s2)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, s1, s2);
      return Progress(result, string);
   }

   bool Progress (bool result, const char *str, const char *s1, int d1)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, s1, d1);
      return Progress(result, string);
   }

   bool Progress (bool result, const char *str, int d1, const char *s1)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, d1, s1);
      return Progress(result, string);
   }

   bool ProgressI64 (bool result, const char *str, int64_t val)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, val);
      return Progress(result, string);
   }

   bool ProgressFloat (bool result, const char *str, float val)
   {
      char string[MAX_STR_LEN-100];
      snprintf(string, sizeof(string), str, val);
      return Progress(result, string);
   }

   void Ok (const char *string)
   {
      Progress(true, string);
   }

   void Fail (const char *string)
   {
      Progress(false, string);
   }

   void TestHeader (const char *string)
   {
      int len = strlen(string);
      int leftspace = (totallen - len)/2 - 2;
      int rightspace = totallen - 4 - len - leftspace;
      int i;

      printf("\n\n");
      for(i=0; i<totallen; i++) printf("*");
      printf("\n**");
      for(i=0; i<leftspace; i++) printf(" ");   //forgot printf-specifier..
      printf("%s", string);
      for(i=0; i<rightspace; i++) printf(" ");
      printf("**\n");
      for(i=0; i<totallen; i++) printf("*");
      printf("\n");
   }
};
