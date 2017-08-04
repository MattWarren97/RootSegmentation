@SET JAVA_HOME=C:\Program Files\Java
@FOR /F %%G IN ('DIR /B "%JAVA_HOME%\JDK*"') DO @SET JDK_HOME=%JAVA_HOME%\%%G
@SET PATH=%JDK_HOME%\bin;%PATH%

@SET JAVA_HOME_2=C:\Program Files (x86)\Java
@FOR /F %%G IN ('DIR /B "%JAVA_HOME_2%\JDK*"') DO @SET JDK_HOME_2=%JAVA_HOME_2%\%%G
@SET PATH=%JDK_HOME_2%\bin;%PATH%

@javac -version
@echo   both options added to System path succesfully


@JAVAC %1 -cp \\Roose_group\M_Warren\Fiji_201610_JAVA8.app\jars\ij-1.51g.jar;\\Roose_group\M_Warren\RootSegmentation\jars\commons-collections-3.2.2.jar;\\Roose_group\M_Warren\RootSegmentation\ -d \\Roose_group\M_Warren\Fiji_201610_JAVA8.app\plugins
