# Запуск проекта через Docker и публикация на GitHub

## 1. Запуск через Docker

Требования:

- Docker Desktop;
- открытый Docker Engine.

В папке проекта выполнить:

```bash
docker compose up --build
```

После запуска приложение будет доступно по адресу:

```text
http://localhost:8080
```

MySQL будет доступен на:

```text
host: localhost
port: 3306
database: uas_db
user: uas
password: uas
```

Остановить контейнеры:

```bash
docker compose down
```

Полностью удалить контейнеры и данные MySQL:

```bash
docker compose down -v
```

## 2. Быстрый запуск на Windows

Можно запустить файл:

```text
run-docker.bat
```

Для остановки:

```text
stop-docker.bat
```

## 3. Публикация проекта на GitHub

В папке проекта выполнить:

```bash
git init
git add .
git commit -m "Initial commit: UAS simulator web app"
git branch -M main
git remote add origin https://github.com/USERNAME/REPOSITORY.git
git push -u origin main
```

`USERNAME` и `REPOSITORY` заменить на свои данные GitHub.

## 4. Что не нужно загружать в GitHub

Файл `.gitignore` уже исключает:

- `target/`;
- `.idea/`;
- `.vscode/`;
- `data/`;
- временные файлы и логи.

## 5. Проверка Docker-сборки

```bash
docker compose ps
docker logs uas-app
docker logs uas-mysql
```

Если приложение успешно запустилось, в логах будет строка о запуске Tomcat на порту `8080`.
