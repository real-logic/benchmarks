#
# Copyright 2015-2020 Real Logic Limited.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
cmake_minimum_required(VERSION 3.1.3 FATAL_ERROR)
cmake_policy(VERSION 3.1.3)

if (${CMAKE_SOURCE_DIR} STREQUAL ${CMAKE_BINARY_DIR})
    message(FATAL_ERROR "In-source builds not allowed. Please make a new directory (called a build directory)"
        "and run CMake from there. You may need to remove CMakeCache.txt.")
endif ()

include(ExternalProject)

project("benchmarks")

# default build type is Release
if (NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE "Release" CACHE STRING "Choose the type of build" FORCE)
endif (NOT CMAKE_BUILD_TYPE)

set(AERON_THIRDPARTY_BINARY_DIR "${CMAKE_CURRENT_BINARY_DIR}/thirdparty")

##########################################################
# Google benchmark

ExternalProject_Add(
    google_benchmark
    URL ${CMAKE_CURRENT_SOURCE_DIR}/cppbuild/benchmark-1.5.zip
    URL_MD5 eec90889668c3b9908fe4110a0940eed
    CMAKE_ARGS -DCMAKE_C_COMPILER=${CMAKE_C_COMPILER};-DCMAKE_CXX_COMPILER=${CMAKE_CXX_COMPILER};
    -DBENCHMARK_ENABLE_TESTING=OFF;-DBENCHMARK_ENABLE_GTEST_TESTS=OFF;-DBENCHMARK_ENABLE_ASSEMBLY_TESTS=OFF;
    -DCMAKE_BUILD_TYPE=Release
    PREFIX "${AERON_THIRDPARTY_BINARY_DIR}/google_benchmark"
    BUILD_BYPRODUCTS "${AERON_THIRDPARTY_BINARY_DIR}/google_benchmark/src/google_benchmark-build/src/${CMAKE_STATIC_LIBRARY_PREFIX}benchmark${CMAKE_STATIC_LIBRARY_SUFFIX}"
    INSTALL_COMMAND ""
)

ExternalProject_Get_Property(google_benchmark source_dir)
set(GOOGLE_BENCHMARK_SOURCE_DIR ${source_dir})
ExternalProject_Get_Property(google_benchmark binary_dir)
set(GOOGLE_BENCHMARK_BINARY_DIR ${binary_dir})

set(
    GOOGLE_BENCHMARK_LIBS
    ${GOOGLE_BENCHMARK_BINARY_DIR}/src/${CMAKE_STATIC_LIBRARY_PREFIX}benchmark${CMAKE_STATIC_LIBRARY_SUFFIX}
)

##########################################################
# HDR Histogram

ExternalProject_Add(
    hdr_histogram
    URL ${CMAKE_CURRENT_SOURCE_DIR}/cppbuild/HdrHistogram_c-0.9.13.zip
    URL_MD5 75954aba7caf5d4bbdb70bcbcef15a8a
    CMAKE_ARGS -DCMAKE_C_COMPILER=${CMAKE_C_COMPILER};-DCMAKE_CXX_COMPILER=${CMAKE_CXX_COMPILER};-DCMAKE_C_STANDARD=99;-DCMAKE_PREFIX_PATH=${CMAKE_CURRENT_BINARY_DIR}/zlib64
    PREFIX "${AERON_THIRDPARTY_BINARY_DIR}/hdr_histogram"
    BUILD_BYPRODUCTS "${AERON_THIRDPARTY_BINARY_DIR}/hdr_histogram/src/hdr_histogram-build/src/${CMAKE_CFG_INTDIR}/${CMAKE_STATIC_LIBRARY_PREFIX}hdr_histogram_static${CMAKE_STATIC_LIBRARY_SUFFIX}"
    INSTALL_COMMAND ""
)

ExternalProject_Get_Property(hdr_histogram source_dir)
set(HDRHISTOGRAM_SOURCE_DIR ${source_dir})
ExternalProject_Get_Property(hdr_histogram binary_dir)
set(HDRHISTOGRAM_BINARY_DIR ${binary_dir})

set(
    HDRHISTOGRAM_LIBS
    ${HDRHISTOGRAM_BINARY_DIR}/src/${CMAKE_CFG_INTDIR}/${CMAKE_STATIC_LIBRARY_PREFIX}hdr_histogram_static${CMAKE_STATIC_LIBRARY_SUFFIX}
)

##########################################################
# Aeron from github or local git

if (NOT AERON_GIT_URL)
    set(AERON_GIT_URL "https://github.com/real-logic/aeron.git" CACHE STRING "Location of the Aeron git repo" FORCE)
endif (NOT AERON_GIT_URL)

if (NOT AERON_GIT_SHA)
    if (NOT AERON_GIT_TAG)
        set(AERON_GIT_TAG "origin/HEAD" CACHE STRING "Tag in the Aeron git repo" FORCE)
    endif (NOT AERON_GIT_TAG)

    ExternalProject_Add(
        aeron
        GIT_REPOSITORY ${AERON_GIT_URL}
        GIT_TAG ${AERON_GIT_TAG}
        GIT_SHALLOW TRUE
        CMAKE_ARGS "-DAERON_TESTS=OFF" "-DAERON_SYSTEM_TESTS=OFF" "-DAERON_SLOW_SYSTEM_TESTS=OFF" "-DCMAKE_BUILD_TYPE=Release"
        INSTALL_COMMAND ""
    )
else ()
    ExternalProject_Add(
        aeron
        GIT_REPOSITORY ${AERON_GIT_URL}
        GIT_TAG ${AERON_GIT_SHA}
        CMAKE_ARGS "-DAERON_TESTS=OFF" "-DAERON_SYSTEM_TESTS=OFF" "-DAERON_SLOW_SYSTEM_TESTS=OFF" "-DCMAKE_BUILD_TYPE=Release"
        INSTALL_COMMAND ""
    )
endif ()

ExternalProject_Get_Property(aeron source_dir)
set(AERON_SOURCE_DIR ${source_dir})
ExternalProject_Get_Property(aeron binary_dir)
set(AERON_BINARY_DIR ${binary_dir})

set(
    AERON_LIBS
    ${AERON_BINARY_DIR}/${CMAKE_CFG_INTDIR}/lib/${CMAKE_STATIC_LIBRARY_PREFIX}aeron_client${CMAKE_STATIC_LIBRARY_SUFFIX}
    ${AERON_BINARY_DIR}/${CMAKE_CFG_INTDIR}/lib/${CMAKE_STATIC_LIBRARY_PREFIX}aeron_driver${CMAKE_SHARED_LIBRARY_SUFFIX}
)

##########################################################
# Platform flags, etc.

find_package(Threads)

if (NOT DEFINED CMAKE_CXX_STANDARD)
    set(CMAKE_CXX_STANDARD 11)
endif ()

if (NOT DEFINED CMAKE_CXX_EXTENSIONS)
    set(CMAKE_CXX_EXTENSIONS OFF)
endif ()

if (NOT DEFINED CMAKE_C_STANDARD)
    set(CMAKE_C_STANDARD 11)
endif ()

if (NOT DEFINED CMAKE_C_EXTENSIONS)
    set(CMAKE_C_EXTENSIONS OFF)
endif ()

# all UNIX-based platform compiler flags
if (UNIX)
    add_compile_options(-Wall -Wpedantic -Wextra -Wno-unused-parameter)
    #    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Ofast -DDISABLE_BOUNDS_CHECKS")
    add_compile_options($<$<CONFIG:Release>:-Ofast>)
endif ()

# platform specific flags
if (APPLE)
    add_compile_options(-Wsign-compare)
    add_definitions(-DDarwin)
    add_compile_options(-Wno-deprecated-register)
elseif (CYGWIN)
    add_definitions(-DWIN32)
    #add_definitions(-DGTEST_HAS_PTHREAD)
    string(REPLACE "-std=c++11" "-std=gnu++11" CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS})
elseif (MSVC)
    add_definitions(-DWIN32)
    add_definitions(-D_CRT_SECURE_NO_WARNINGS)
    add_definitions(-DNOMINMAX)

    set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} /MTd /Od /Zi")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} /MT")
endif ()

##########################################################
# Project variables, etc.

set(CMAKE_RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/binaries")
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_CURRENT_BINARY_DIR}/lib")

# google benchmark
include_directories(${GOOGLE_BENCHMARK_SOURCE_DIR}/include)
# Hdr Histogram
include_directories(${HDRHISTOGRAM_SOURCE_DIR}/src)
include_directories(${AERON_SOURCE_DIR}/aeron-client/src/main/cpp)
include_directories(${AERON_SOURCE_DIR}/aeron-client/src/main/c)
include_directories(${AERON_SOURCE_DIR}/aeron-driver/src/main/c)

include_directories(${CMAKE_SOURCE_DIR}/benchmarks-aeron/src/main/cpp)

##########################################################

add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/benchmarks-aeron/src/main/cpp")
