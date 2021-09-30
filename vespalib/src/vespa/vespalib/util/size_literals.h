// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

constexpr size_t operator "" _Ki(unsigned long long k_in) {
	return size_t(k_in << 10u);
}

constexpr size_t operator "" _Mi(unsigned long long m_in) {
	return size_t(m_in << 20u);
}

constexpr size_t operator "" _Gi(unsigned long long g_in) {
	return size_t(g_in << 30u);
}

constexpr size_t operator "" _Ti(unsigned long long t_in) {
	return size_t(t_in << 40u);
}
