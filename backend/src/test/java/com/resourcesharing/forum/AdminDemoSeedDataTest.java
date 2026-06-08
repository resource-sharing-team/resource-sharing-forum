package com.resourcesharing.forum;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdminDemoSeedDataTest {
    @Test
    void flywaySeedProvidesRealAdminDemoRowsForFrontendPages() throws IOException {
        String seed = Files.readString(Path.of("src/main/resources/db/migration/V6__seed_admin_demo_data.sql"));

        assertThat(seed).contains(
                "ALTER TABLE resource_info DROP CHECK ck_resource_status",
                "REVIEWING_RISK",
                "COPYRIGHT_DOWN",
                "INSERT INTO user_account",
                "disabled_user",
                "locked_user",
                "INSERT INTO resource_info",
                "UI设计全套模板",
                "办公表格合集",
                "PENDING_REVIEW",
                "OFFLINE",
                "INSERT INTO comment_info",
                "INSERT INTO request_post",
                "INSERT INTO request_reply",
                "INSERT INTO report_complaint",
                "REQUEST_REPLY",
                "COPYRIGHT",
                "INSERT INTO appeal_record",
                "INSERT INTO system_config",
                "point.resource_approved",
                "INSERT INTO admin_operation_log",
                "RESOURCE_APPROVE",
                "MEMBER_DISABLED",
                "REPORT_HANDLE",
                "APPEAL_HANDLE"
        );
    }

    @Test
    void flywaySeedProvidesRealUserFrontendDemoRows() throws IOException {
        String seed = Files.readString(Path.of("src/main/resources/db/migration/V7__seed_user_frontend_demo_data.sql"));
        String categoryAlignment = Files.readString(Path.of("src/main/resources/db/migration/V8__align_frontend_category_catalog.sql"));
        String normalizedCatalog = Files.readString(Path.of("src/main/resources/db/migration/V9__normalize_user_category_catalog.sql"));
        String normalizedTags = Files.readString(Path.of("src/main/resources/db/migration/V10__normalize_demo_tags_and_extend_seed_data.sql"));

        assertThat(seed).contains(
                "UI 设计全套 Figma 模板合集",
                "Python 数据分析入门到实战教程",
                "极简个人简历模板合集 100 套",
                "Vue3 后台管理系统模板",
                "高清风景摄影图集",
                "求 2026 教资面试结构化真题及解析",
                "求 Python 数据分析实战项目源码",
                "求 B 端产品 UI 设计规范文档",
                "求 Java 微服务项目实战教程",
                "INSERT INTO user_interaction",
                "INSERT INTO system_notice",
                "INSERT INTO login_record"
        );
        assertThat(categoryAlignment).contains(
                "UI设计",
                "图片素材",
                "IT教程",
                "ON DUPLICATE KEY UPDATE"
        );
        assertThat(normalizedCatalog).contains(
                "(1, NULL, '文档资料', 1, 'ENABLED', 1)",
                "(2, NULL, '设计素材', 1, 'ENABLED', 2)",
                "(3, NULL, '源码模板', 1, 'ENABLED', 3)",
                "(4, NULL, '教程学习', 1, 'ENABLED', 4)",
                "(5, NULL, '软件工具', 1, 'ENABLED', 5)",
                "(21, 2, 'UI设计', 2, 'ENABLED', 1)",
                "(22, 2, '图片素材', 2, 'ENABLED', 2)",
                "(41, 4, 'IT教程', 2, 'ENABLED', 1)",
                "(51, 5, '开发工具', 2, 'ENABLED', 1)",
                "(53, 5, '效率工具', 2, 'ENABLED', 3)"
        );
        assertThat(normalizedTags).contains(
                "first-level category + second-level category + resource type",
                "DELETE FROM resource_tag_rel WHERE resource_id BETWEEN 1 AND 23",
                "DELETE FROM request_tag_rel WHERE request_id IN (1, 2, 3, 4) OR request_id BETWEEN 10 AND 21",
                "JSON_ARRAY('文档资料', '学习笔记', '文档')",
                "JSON_ARRAY('设计素材', '字体图标', '素材')",
                "JSON_ARRAY('源码模板', '后端源码', '源码')",
                "JSON_ARRAY('源码模板', '完整项目', '源码')",
                "JSON_ARRAY('软件工具', '开发工具', '链接')",
                "'文档资料', '设计素材', '源码模板', '教程学习', '软件工具'",
                "'文档', '软件', '源码', '素材', '教程', '模板', '链接'"
        );
    }
}
