@echo off
rem PackWeaver Bridge build script: JDK and Gradle home are anchored away from C:
set "JAVA_HOME=D:\java\jdk-17\jdk-17.0.5"
set "GRADLE_USER_HOME=E:\packweaker\gradle-home"
call "E:\packweaker\gradle-8.7\bin\gradle.bat" %*
