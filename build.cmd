@echo off
setlocal
set "JAVA_HOME=C:\Repos\newfolder\microsoft-jdk-17.0.16-windows-x64\jdk-17.0.16+8"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Using JAVA_HOME=%JAVA_HOME%
.\gradlew.bat -version --no-daemon
if errorlevel 1 exit /b 1
.\gradlew.bat clean build --stacktrace --no-daemon
exit /b %errorlevel%
