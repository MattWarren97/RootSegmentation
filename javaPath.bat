@SET JAVA_HOME=C:\Program Files\Java
@FOR /F %%G IN ('DIR /B "%JAVA_HOME%\JDK*"') DO @SET JDK_HOME=%JAVA_HOME%\%%G
@SET PATH=%JDK_HOME%\bin;%PATH%

@SET JAVA_HOME_2=C:\Program Files (x86)\Java
@FOR /F %%G IN ('DIR /B "%JAVA_HOME_2%\JDK*"') DO @SET JDK_HOME_2=%JAVA_HOME_2%\%%G
@SET PATH=%JDK_HOME_2%\bin;%PATH%

@javac -version
@echo.
@echo   %JDK_HOME%\bin successfully added to Windows PATH
@echo.
@echo   Now type 'javac'.
@echo.
@echo.
@echo.

@CMD
