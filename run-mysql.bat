@echo off
chcp 65001 > nul

REM Запуск проекта с MySQL.
REM По умолчанию используется база uas_db и пользователь root/root.
REM Если у тебя другой пароль MySQL, поменяй строку MYSQL_PASSWORD ниже.

set MYSQL_HOST=localhost
set MYSQL_PORT=3306
set MYSQL_DATABASE=uas_db
set MYSQL_USER=root
set MYSQL_PASSWORD=root

mvn spring-boot:run -Dspring-boot.run.profiles=mysql
pause
