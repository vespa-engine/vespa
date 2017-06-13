// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once
#include <vespa/log/log.h>

#define STORAGE_LOG_INTERVAL 30

#define STORAGE_LOG_COUNT(name, interval)   do { \
                                static uint64_t C_count ## name = 0; \
                                static time_t C_last ## name = time(NULL); \
                                C_count ## name ++; \
                                time_t C_now ## name = time(NULL); \
                                if (C_now  ## name - C_last ## name >= interval)  { \
                                    EV_COUNT(#name, C_count ## name); \
                                    C_last ## name = C_now ## name;       \
                                } } while (false)

#define STORAGE_LOG_AVERAGE(name, value, interval) do {  \
                                static uint64_t A_count ## name = 0; \
                                static float A_total ## name = 0.0; \
                                static time_t A_last ## name = time(NULL); \
                                A_count ## name ++; \
                                A_total ## name += value; \
                                time_t A_now ## name = time(NULL); \
                                if (A_now  ## name - A_last ## name >= interval)  { \
                                    EV_VALUE(#name, A_total ## name / A_count ## name); \
                                    A_count ## name = 0; \
                                    A_total ## name = 0; \
                                    A_last ## name = A_now ## name;       \
                                }} while (false)

