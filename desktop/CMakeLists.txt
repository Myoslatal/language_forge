cmake_minimum_required(VERSION 3.20)
project(nostongue_forge_desktop LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(WIN32)
  find_package(SDL2 CONFIG REQUIRED)
  find_package(SDL2_ttf CONFIG REQUIRED)
  find_package(SDL2_image CONFIG REQUIRED)
  find_package(SDL2_mixer CONFIG REQUIRED)
  set(SDL2_TARGET SDL2::SDL2)
  set(SDL2_TTF_TARGET SDL2_ttf::SDL2_ttf)
  set(SDL2_IMAGE_TARGET SDL2_image::SDL2_image)
  set(SDL2_MIXER_TARGET SDL2_mixer::SDL2_mixer)
else()
  find_package(PkgConfig REQUIRED)
  pkg_check_modules(SDL2 REQUIRED IMPORTED_TARGET sdl2)
  pkg_check_modules(SDL2_TTF REQUIRED IMPORTED_TARGET SDL2_ttf)
  pkg_check_modules(SDL2_IMAGE REQUIRED IMPORTED_TARGET SDL2_image)
  pkg_check_modules(SDL2_MIXER REQUIRED IMPORTED_TARGET SDL2_mixer)
  set(SDL2_TARGET PkgConfig::SDL2)
  set(SDL2_TTF_TARGET PkgConfig::SDL2_TTF)
  set(SDL2_IMAGE_TARGET PkgConfig::SDL2_IMAGE)
  set(SDL2_MIXER_TARGET PkgConfig::SDL2_MIXER)
endif()

set(IMGUI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/imgui")
set(MINIZ_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/miniz")
set(NLOHMANN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party")

add_executable(nostongue-forge
  src/main.cpp
  src/model.cpp
  src/vocabulary.cpp
  src/lexicon.cpp
  src/images.cpp
  src/audio.cpp
  src/pos.cpp
  ${IMGUI_DIR}/imgui.cpp
  ${IMGUI_DIR}/imgui_draw.cpp
  ${IMGUI_DIR}/imgui_tables.cpp
  ${IMGUI_DIR}/imgui_widgets.cpp
  ${IMGUI_DIR}/backends/imgui_impl_sdl2.cpp
  ${IMGUI_DIR}/backends/imgui_impl_sdlrenderer2.cpp
  ${MINIZ_DIR}/miniz.c
  ${MINIZ_DIR}/miniz_tdef.c
  ${MINIZ_DIR}/miniz_tinfl.c
  ${MINIZ_DIR}/miniz_zip.c
)

target_include_directories(nostongue-forge PRIVATE
  src
  ${IMGUI_DIR}
  ${IMGUI_DIR}/backends
  ${MINIZ_DIR}
  ${NLOHMANN_DIR}
)

target_link_libraries(nostongue-forge PRIVATE
  ${SDL2_TARGET}
  ${SDL2_TTF_TARGET}
  ${SDL2_IMAGE_TARGET}
  ${SDL2_MIXER_TARGET}
)

if(WIN32)
  if(TARGET SDL2::SDL2main)
    target_link_libraries(nostongue-forge PRIVATE SDL2::SDL2main)
  endif()
  set_target_properties(nostongue-forge PROPERTIES WIN32_EXECUTABLE TRUE)
  target_link_libraries(nostongue-forge PRIVATE shell32)
endif()

if(MSVC)
  target_compile_options(nostongue-forge PRIVATE /utf-8 /W3)
else()
  target_compile_options(nostongue-forge PRIVATE -Wall -Wextra -Wno-unused-parameter)
endif()