cmake_minimum_required(VERSION 3.22.1)

project(http_proxy LANGUAGES CXX)

add_library(http_proxy SHARED http_proxy.cpp)

target_compile_features(http_proxy PRIVATE cxx_std_20)
target_link_libraries(http_proxy PRIVATE log)
