@if "%DEBUG%" == "" @echo off
setlocal EnableDelayedExpansion

set SOURCE_DIR=%CD%
set BUILD_DIR=%CD%\cppbuild\Release
set ZLIB_ZIP=%CD%\cppbuild\zlib1211.zip
set BUILD_DIR=%CD%\cppbuild\Release
set ZLIB_BUILD_DIR=%BUILD_DIR%\zlib-build
set ZLIB_INSTALL_DIR=%BUILD_DIR%\zlib64
set EXTRA_CMAKE_ARGS=

for %%o in (%*) do (

    if "%%o"=="--help" (
        echo %0 cppbuild.cmd [--c-warnings-as-errors] [--cxx-warnings-as-errors] [--debug-build] [--aeron-git-url $GIT_URL] [--aeron-git-tag $GIT_TAG]
        exit /b
    )

    if "%%o"=="--c-warnings-as-errors" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DC_WARNINGS_AS_ERRORS=ON
    )

    if "%%o"=="--cxx-warnings-as-errors" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DCXX_WARNINGS_AS_ERRORS=ON
    )

    if "%%o"=="--debug-build" (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DCMAKE_BUILD_TYPE=Debug
    )

    if defined gitUrl (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_URL=%%o
        set "gitUrl="
    )

    if "%%o"=="--aeron-git-url" (
        set gitUrl=1
    )

    if defined gitTag (
        set EXTRA_CMAKE_ARGS=!EXTRA_CMAKE_ARGS! -DAERON_GIT_TAG=%%o
        set "gitTag="
    )

    if "%%o"=="--aeron-git-tag" (
        set gitTag=1
    )
)

call %CD%\cppbuild\vs-helper.cmd
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
