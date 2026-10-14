@echo off
REM Requiere JDK 21 y Maven instalados y en el PATH
mvn clean package
echo.
echo Listo: target\ArcLogin.jar  ^(copialo a la carpeta plugins de tu server^)
pause
