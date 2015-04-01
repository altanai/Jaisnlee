@echo off

rem -------------------------------------------------------------------------
rem Start Rhino Script for Windows
rem -------------------------------------------------------------------------


rem This batch script sets up your freshly unzipped Rhino SDK directory
rem so that Rhino will actually run.

rem %~dp0 is the expanded pathname of the current script's directory.
set RHINO_HOME=%~dp0
set PRODUCT_NAME="Rhino SLEE SDK"
rem Uncomment this option to turn on startup debugging.
rem set DEBUG_STARTUP="-Drhino.startup.debug=true"

:TOP
set JAVA_HOME_FILE=work\javahome.txt
IF EXIST %JAVA_HOME_FILE% (
   set /P JAVA_HOME=< %JAVA_HOME_FILE%
   echo Found JAVA_HOME=%JAVA_HOME% in %JAVA_HOME_FILE%
)

IF EXIST "%1" (
   set JAVA_HOME="%1"
   echo %JAVA_HOME%> work\javahome.txt
)

call :CHECK_JAVA_HOME

rem Check to make sure the Rhino SLEE is not being started.
set PIDFILE="%RHINO_HOME%\work\rhino.pid"
IF EXIST %PIDFILE% (
         echo Is Rhino SLEE already running? If yes and the Java virtual machine was 
         echo terminated by the TerminateProcess call, e.g., close or CTRL_C on batch  
         echo window on Microsoft Windows, you need to run the stop-rhino script 
         echo to terminate the Rhino SLEE via management commands. If no, please delete the 
         echo %PIDFILE%, then restart Rhino SLEE.
         goto :EOF
)
call :RUN_START_SCRIPT

GOTO :EOF

:CHECK_JAVA_HOME
rem -------------------------------------------------------------------------
rem Checking JAVA_HOME
rem -------------------------------------------------------------------------
IF NOT DEFINED JAVA_HOME (
       echo The JAVA_HOME environment variable is not defined. 
       echo Please set it to the location of a Sun JDK "(version 1.6 or later)."
       GOTO :GET_JAVA_HOME
)
IF NOT EXIST %JAVA_HOME% (
       echo %JAVA_HOME% does not exist.
       echo You must set the JAVA_HOME environment variable to the location of a Sun JDK.
       echo Please make sure that the JAVA_HOME environment variable does not contain a space in the path.
       GOTO :GET_JAVA_HOME
)

set JAVA=%JAVA_HOME%\bin\java.exe
set RUNTIME_CLASSPATH=%RUNTIME_CLASSPATH%\lib\ojdbc6.jar:%RUNTIME_CLASSPATH%\lib\postgresql.jar
echo Validating JAVA_HOME: %JAVA_HOME%
%JAVA% -classpath "%RHINO_HOME%\lib\rhino-tools.jar" com.opencloud.rhino.tools.ValidateJavaHome %JAVA_HOME%
echo Sun JDK ok
GOTO :EOF

:GET_JAVA_HOME
rem -------------------------------------------------------------------------
rem Getting JAVA_HOME
rem -------------------------------------------------------------------------
echo Enter the location of your Java J2SE/JDK installation.
echo This must be at least version 1.6.0.
echo.
echo If the path includes spaces (like "C:\Program Files\Java\jdk1.6.0"), put in the DOS path instead. for example:
echo     C:\Progra~1\Java\jdk1.6.0
set /P JAVA_HOME=Path: 
IF NOT EXIST %JAVA_HOME% (
   echo The JAVA_HOME environment variable does not exist.
   set JAVA_HOME=
   goto :TOP 
)
IF NOT EXIST work (
   mkdir work
)
echo %JAVA_HOME%> work\javahome.txt
echo.
GOTO :CHECK_JAVA_HOME

:RUN_START_SCRIPT
rem -------------------------------------------------------------------------
rem Running start-rhino script
rem -------------------------------------------------------------------------
echo Running rhino-tools.jar
%JAVA% -cp "%RHINO_HOME%\lib\rhino-tools.jar;%RHINO_HOME%\lib\RhinoSDKBoot.jar;%RHINO_HOME%\lib\derby.jar;%RHINO_HOME%\lib\postgresql.jar;%RHINO_HOME%\lib\ojdbc6.jar" %DEBUG_STARTUP% com.opencloud.rhino.tools.StartRhinoProcess %JAVA_HOME%
GOTO :EOF
PAUSE
