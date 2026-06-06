# Database Schema Summary

The production profile deploys the schema through Flyway migrations in `src/main/resources/db/migration`. The target database is MySQL 8.x with `utf8mb4`.

## Core Tables

| Module | Tables |
| --- | --- |
| Identity | `user_account`, `member_profile`, `administrator_profile`, `login_record`, `membership_level` |
| Points | `member_point_account`, `point_flow` |
| Resource and file | `resource_info`, `resource_version`, `resource_category`, `tag_info`, `resource_tag_rel`, `file_attachment`, `resource_audit_record`, `resource_status_log`, `download_record`, `resource_rating` |
| Interaction | `user_interaction`, `comment_info` |
| Request reward | `request_post`, `request_tag_rel`, `request_reply`, `request_status_log` |
| Audit and appeal | `report_complaint`, `appeal_record` |
| Notification | `notification_event`, `system_notice` |
| System and monitoring | `admin_operation_log`, `system_config`, `platform_announcement`, `email_verification_code`, `sensitive_word`, `search_log`, `resource_daily_stat`, `member_daily_stat` |

## Design Rules Captured In Migrations

- Primary keys use unsigned big integer ids.
- Usernames and emails are unique.
- Passwords are stored as hashes, never plaintext.
- Resource and request status changes are traceable through status-log tables.
- Point changes are traceable through `point_flow`.
- File binaries are not stored in MySQL; `file_attachment` stores metadata and server-side paths only.
- High-frequency queries have indexes for status/time, owner, target, and unread notification access.
- Full-text indexes are present for resource and request search.
- Notification events are separated from user-visible notices through `notification_event` and `system_notice`.

## Deployment Notes

- `application-prod.yml` enables Flyway and disables automatic SQL init.
- `docker-compose.yml` starts MySQL 8 with `utf8mb4_unicode_ci`.
- Persistent database files live in the `mysql_data` Compose volume.
- Uploaded files live outside MySQL in the `backend_uploads` Compose volume.
