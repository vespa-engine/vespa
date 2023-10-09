// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <util/timer.h>
#include <util/clientstatus.h>
#include <fbench/client.h>

int
main(int argc, char **argv)
{
  (void) argc;
  (void) argv;

  ClientStatus *status = new ClientStatus;

  printf("adding response time: %d\n", 0);
  status->ResponseTime(0);
  printf("adding response time: %d\n", 1000);
  status->ResponseTime(1000);
  printf("adding response time: %d\n", 2000);
  status->ResponseTime(2000);
  printf("adding response time: %d\n", 3000);
  status->ResponseTime(3000);
  printf("adding response time: %d\n", 4000);
  status->ResponseTime(4000);
  printf("adding response time: %d\n", 5000);
  status->ResponseTime(5000);
  printf("adding response time: %d\n", 6000);
  status->ResponseTime(6000);
  printf("adding response time: %d\n", 7000);
  status->ResponseTime(7000);
  printf("adding response time: %d\n", 8000);
  status->ResponseTime(8000);
  printf("adding response time: %d\n", 9000);
  status->ResponseTime(9000);
  printf("adding response time: %d\n", 10000);
  status->ResponseTime(10000);

  printf("  0%% percentile: %8.2f\n", status->GetPercentile(0));
  printf("  5%% percentile: %8.2f\n", status->GetPercentile(5));
  printf(" 10%% percentile: %8.2f\n", status->GetPercentile(10));
  printf(" 15%% percentile: %8.2f\n", status->GetPercentile(15));
  printf(" 20%% percentile: %8.2f\n", status->GetPercentile(20));
  printf(" 25%% percentile: %8.2f\n", status->GetPercentile(25));
  printf(" 30%% percentile: %8.2f\n", status->GetPercentile(30));
  printf(" 35%% percentile: %8.2f\n", status->GetPercentile(35));
  printf(" 40%% percentile: %8.2f\n", status->GetPercentile(40));
  printf(" 45%% percentile: %8.2f\n", status->GetPercentile(45));
  printf(" 50%% percentile: %8.2f\n", status->GetPercentile(50));
  printf(" 55%% percentile: %8.2f\n", status->GetPercentile(55));
  printf(" 60%% percentile: %8.2f\n", status->GetPercentile(60));
  printf(" 65%% percentile: %8.2f\n", status->GetPercentile(65));
  printf(" 70%% percentile: %8.2f\n", status->GetPercentile(70));
  printf(" 75%% percentile: %8.2f\n", status->GetPercentile(75));
  printf(" 80%% percentile: %8.2f\n", status->GetPercentile(80));
  printf(" 85%% percentile: %8.2f\n", status->GetPercentile(85));
  printf(" 90%% percentile: %8.2f\n", status->GetPercentile(90));
  printf(" 95%% percentile: %8.2f\n", status->GetPercentile(95));
  printf("100%% percentile: %8.2f\n", status->GetPercentile(100));

  delete status;
}
