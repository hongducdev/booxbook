cmake_minimum_required(VERSION 3.22.1)

project("azw3_bridge" C CXX)

set(CMAKE_C_STANDARD 99)
set(CMAKE_CXX_STANDARD 17)

# libmobi definitions
add_definitions(-DPACKAGE_VERSION="0.12")
add_definitions(-DUSE_ENCRYPTION)
add_definitions(-DUSE_XMLWRITER)
add_definitions(-DUSE_ZLIB)
add_definitions(-DHAVE_UNISTD_H)
add_definitions(-DHAVE_STRDUP)
add_definitions(-DHAVE_SYS_RESOURCE_H)
add_definitions(-DMINIZ_NO_TIME)
add_definitions(-DMINIZ_NO_ZLIB_COMPATIBLE_NAMES)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/libmobi
)

set(LIBMOBI_SOURCES
    libmobi/buffer.c
    libmobi/compression.c
    libmobi/debug.c
    libmobi/encryption.c
    libmobi/index.c
    libmobi/memory.c
    libmobi/meta.c
    libmobi/miniz.c
    libmobi/opf.c
    libmobi/parse_rawml.c
    libmobi/randombytes.c
    libmobi/read.c
    libmobi/sha1.c
    libmobi/structure.c
    libmobi/util.c
    libmobi/write.c
    libmobi/xmlwriter.c
)

add_library(azw3_bridge SHARED
    azw3_bridge.cpp
    ${LIBMOBI_SOURCES}
)

find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(azw3_bridge
    ${log-lib}
    ${z-lib}
)
