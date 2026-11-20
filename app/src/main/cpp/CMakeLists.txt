cmake_minimum_required(VERSION 3.22.1)

project("txtify_native")

add_library(${CMAKE_PROJECT_NAME} SHARED
        native-lib.cpp)

target_compile_options(${CMAKE_PROJECT_NAME} PRIVATE
        -fvisibility=hidden
        -ffunction-sections
        -fdata-sections
        -O3)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        -Wl,--gc-sections
        -Wl,-z,max-page-size=16384
        $<$<CONFIG:Release>:-s>)

find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib})