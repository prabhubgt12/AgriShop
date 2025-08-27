@echo off
setlocal enabledelayedexpansion

rem Configure SDK root (no spaces in path)
set "SDK=C:\Repos\newfolder\AndroidSdk"
set "SDKMAN=%SDK%\cmdline-tools\latest\bin\sdkmanager.bat"
rem Force Java 17 for sdkmanager
set "JAVA_HOME=C:\Repos\newfolder\microsoft-jdk-17.0.16-windows-x64\jdk-17.0.16+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo.
echo [1/4] Verifying sdkmanager at "%SDKMAN%"
if not exist "%SDKMAN%" (
  echo ERROR: sdkmanager not found at "%SDKMAN%"
  echo Ensure commandline-tools were extracted to %SDK%\cmdline-tools\latest
  exit /b 1
)

echo.
echo [2/4] sdkmanager version
call "%SDKMAN%" --sdk_root="%SDK%" --version || exit /b 1

echo.
echo [3/4] Installing platform-tools, platforms;android-34, build-tools
rem Try 34.0.2 then fall back to 34.0.1 and 34.0.0
call "%SDKMAN%" --sdk_root="%SDK%" "platform-tools" "platforms;android-34" "build-tools;34.0.2"
if errorlevel 1 (
  echo build-tools;34.0.2 not available, trying 34.0.1...
  call "%SDKMAN%" --sdk_root="%SDK%" "platform-tools" "platforms;android-34" "build-tools;34.0.1"
)
if errorlevel 1 (
  echo build-tools;34.0.1 not available, trying 34.0.0...
  call "%SDKMAN%" --sdk_root="%SDK%" "platform-tools" "platforms;android-34" "build-tools;34.0.0" || exit /b 1
)

echo.
echo [4/4] Accepting SDK licenses
set "YESFILE=%TEMP%\sdk_licenses_yes.txt"
>"%YESFILE%" (
  for /L %%i in (1,1,200) do @echo y
)
"%SDKMAN%" --sdk_root="%SDK%" --licenses < "%YESFILE%" || exit /b 1
del /q "%YESFILE%" >nul 2>&1

rem Retry installs now that licenses are accepted
call "%SDKMAN%" --sdk_root="%SDK%" "platform-tools" "platforms;android-34"
if errorlevel 1 exit /b 1
call "%SDKMAN%" --sdk_root="%SDK%" "build-tools;34.0.2"
if errorlevel 1 call "%SDKMAN%" --sdk_root="%SDK%" "build-tools;34.0.1"
if errorlevel 1 call "%SDKMAN%" --sdk_root="%SDK%" "build-tools;34.0.0" || exit /b 1

echo.
echo Writing local.properties with sdk.dir
echo sdk.dir=C:\\Repos\\newfolder\\AndroidSdk> "%~dp0local.properties"

echo.
echo SDK setup complete.
exit /b 0
