@if "%DEBUG%" == "" @echo off
setlocal EnableDelayedExpansion

set "DIR=%~dp0"
set "SOURCE_DIR=%DIR%\.."
set "BUILD_DIR=%DIR%\Release"
set "BUILD_CONFIG=Release"
set "EXTRA_CMAKE_ARGS="
set "CMAKE_BUILD_PARALLEL_LEVEL=%NUMBER_OF_PROCESSORS%"

:loop
if not "%1"=="" (
    if "%1"=="--help" (
        echo %0 [--c-warnings-as-errors] [--cxx-warnings-as-errors] [--debug-build] [--aeron-git-url $GIT_URL] [--aeron-git-tag $GIT_TAG] [--aeron-git-sha $GIT_SHA] [--help]
        exit /b
    ) else if "%1"=="--c-warnings-as-errors" (
        set "EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DC_WARNINGS_AS_ERRORS=ON"
        echo "Enabling warnings as errors for c"
    ) else if "%1"=="--cxx-warnings-as-errors" (
        set "EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DCXX_WARNINGS_AS_ERRORS=ON"
        echo "Enabling warnings as errors for c++"
    ) else if "%1"=="--debug-build" (
        set "EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DCMAKE_BUILD_TYPE=Debug"
        set "BUILD_DIR=%DIR%\Debug"
        set "BUILD_CONFIG=Debug"
        echo "Enabling debug build"
    ) else if "%1"=="--no-parallel" (
        set "CMAKE_BUILD_PARALLEL_LEVEL=1"
        echo "Disabling parallel build"
    ) else if "%1"=="--aeron-git-url" (
        set "EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_URL=%2"
        echo "Setting -DAERON_GIT_URL=%2"
        shift
    ) else if "%1"=="--aeron-git-tag" (
        set "EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_TAG=%2"
        echo "Setting -DAERON_GIT_TAG=%2"
        shift
    ) else if "%1"=="--aeron-git-sha" (
        set "EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_SHA=%2"
        echo "Setting -DAERON_GIT_SHA=%2"
        shift
    ) else (
        echo "Unknown option %%o"
        echo "Use --help for help"
        exit /b 1
    )

    shift
    goto :loop
)

call "%DIR%\vs-helper.cmd"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

if EXIST %BUILD_DIR% rd /S /Q %BUILD_DIR%

md %BUILD_DIR%
pushd %BUILD_DIR%

cmake %EXTRA_CMAKE_ARGS% %SOURCE_DIR%
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

cmake --build . --config %BUILD_CONFIG%
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
