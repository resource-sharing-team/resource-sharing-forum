# Resource Sharing Forum V2 Database

This backend uses a MySQL 8.x schema designed from the V2 database specification.

## Files

- `src/main/resources/schema.sql`: V2 schema, 35 tables.
- `src/main/resources/data.sql`: idempotent development seed data.
- `src/main/resources/db/migration/V1__create_v2_schema.sql`: Flyway schema migration.
- `src/main/resources/db/migration/V2__seed_v2_data.sql`: Flyway seed migration.
- `docker-compose.yml`: local MySQL 8.x development database.

## Start Local MySQL With Docker

```powershell
cd D:\resource_sharing_forum\backend
$env:MYSQL_ROOT_PASSWORD = "root"
docker compose up -d mysql
```

The container creates the `resource_sharing_forum` database with `utf8mb4`.

## Automatic Migration With Spring Boot

Spring Boot uses Flyway migrations from `src/main/resources/db/migration`.

```powershell
cd D:\resource_sharing_forum\backend
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<your-local-password>"
.\mvnw.cmd spring-boot:run
```

`spring.sql.init.mode` is disabled so the legacy `schema.sql` and `data.sql` files are not run twice by Spring. They are kept for manual import and course demonstration.

## Create Database

```sql
CREATE DATABASE resource_sharing_forum
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

## Import With MySQL CLI

```powershell
cd D:\resource_sharing_forum\backend
mysql -uroot -p --default-character-set=utf8mb4 resource_sharing_forum < .\src\main\resources\schema.sql
mysql -uroot -p --default-character-set=utf8mb4 resource_sharing_forum < .\src\main\resources\data.sql
```

If `mysql` is not recognized on Windows, install MySQL Server or MySQL Shell, then add its `bin` directory to `PATH`.

## Import With MySQL Workbench

1. Create the `resource_sharing_forum` database.
2. Open `schema.sql`, execute all statements.
3. Open `data.sql`, execute all statements.
4. Run `SHOW TABLES;` and confirm 35 tables exist.

## Verification Queries

```sql
SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = 'resource_sharing_forum';

SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'resource_sharing_forum'
ORDER BY table_name;

SELECT username, role, status
FROM user_account;

SELECT title, status, published_time
FROM resource_info;
```

## Spring Boot Local Password

`application.yml` reads database settings from environment variables:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<your-local-password>"
```

Do not commit local database passwords into source files.

## Notes

- The schema uses V2 names such as `user_account`, `member_profile`, `resource_info`, `file_attachment`, `request_post`, `request_reply`, `point_flow`, `notification_event`, and `system_notice`.
- File binary content is not stored in the database; `file_attachment.storage_path` stores server-side metadata only.
- Business transitions such as resource review, reward freezing, answer adoption, and report handling must be implemented in backend transactions.
