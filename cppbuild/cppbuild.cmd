@if "%DEBUG%" == "" @echo off
setlocal EnableDelayedExpansion

set "DIR=%~dp0"
set "SOURCE_DIR=%DIR%\.."
set "BUILD_DIR=%DIR%\Release"
set "ZLIB_ZIP=%DIR%\zlib1211.zip"
set "ZLIB_BUILD_DIR=%BUILD_DIR%\zlib-build"
set "ZLIB_INSTALL_DIR=%BUILD_DIR%\zlib64"
set EXTRA_CMAKE_ARGS=

:loop
if not "%1"=="" (
    if "%1"=="--help" (
        echo %0 [--c-warnings-as-errors] [--cxx-warnings-as-errors] [--debug-build] [--aeron-git-url $GIT_URL] [--aeron-git-tag $GIT_TAG]
        exit /b
    )

    if "%1"=="--c-warnings-as-errors" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DC_WARNINGS_AS_ERRORS=ON
    )

    if "%1"=="--cxx-warnings-as-errors" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DCXX_WARNINGS_AS_ERRORS=ON
    )

    if "%1"=="--debug-build" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DCMAKE_BUILD_TYPE=Debug
    )

    if "%1"=="--aeron-git-url" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_URL=%2
        shift
    )

    if "%1"=="--aeron-git-tag" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_TAG=%2
    )

    if "%1"=="--aeron-git-sha" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_SHA=%2
    )

    shift
    goto :loop
)

call "%DIR%\vs-helper.cmd"
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

if EXIST %BUILD_DIR% rd /S /Q %BUILD_DIR%

md %BUILD_DIR%
pushd %BUILD_DIR%
md %ZLIB_BUILD_DIR%
pushd %ZLIB_BUILD_DIR%
7z x %ZLIB_ZIP%
pushd zlib-1.2.11
md build
pushd build

cmake -DCMAKE_INSTALL_PREFIX=%ZLIB_INSTALL_DIR% ..
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

cmake --build . --target install
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

pushd %BUILD_DIR%
cmake %EXTRA_CMAKE_ARGS% %SOURCE_DIR%
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%

cmake --build . --config Release
if %ERRORLEVEL% neq 0 exit /b %ERRORLEVEL%
