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
        "-Wl,--gc-sections"
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
        $<$<CONFIG:Release>:-s>)

# Force maximum page size alignment compatibility for all targets (matched to LunarTag)
set_property(TARGET ${CMAKE_PROJECT_NAME} PROPERTY LINK_FLAGS "-Wl,-z,max-page-size=16384")

find_library(log-lib log)

target_link_libraries(${CMAKE_PROJECT_NAME}
        ${log-lib})