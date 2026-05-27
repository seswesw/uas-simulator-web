@echo off
chcp 65001 > nul

REM Старый режим с локальной файловой базой data/uas-db.mv.db.
REM Используй только если специально нужен H2.

mvn spring-boot:run -Dspring-boot.run.profiles=h2
pause
