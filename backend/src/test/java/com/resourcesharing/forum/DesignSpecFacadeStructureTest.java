package com.resourcesharing.forum;

import com.resourcesharing.forum.service.DesignSpecForumService;
import com.resourcesharing.forum.service.LegacyDesignSpecForumService;
import com.resourcesharing.forum.service.NotificationService;
import com.resourcesharing.forum.service.audit.AppealService;
import com.resourcesharing.forum.service.audit.ReportComplaintService;
import com.resourcesharing.forum.service.identity.AuthService;
import com.resourcesharing.forum.service.identity.MemberService;
import com.resourcesharing.forum.service.interaction.InteractionService;
import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.notification.NotificationEventService;
import com.resourcesharing.forum.service.request.RequestRewardService;
import com.resourcesharing.forum.service.resource.ResourceQueryService;
import com.resourcesharing.forum.service.system.AdminCatalogService;
import com.resourcesharing.forum.service.system.AdminLogService;
import com.resourcesharing.forum.service.system.AdminMemberService;
import com.resourcesharing.forum.service.system.AdminSystemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DesignSpecFacadeStructureTest {
    @Autowired
    private DesignSpecForumService forumService;

    @Autowired
    private LegacyDesignSpecForumService legacyService;

    @Test
    void facadeDelegatesToDesignSpecModulesInsteadOfLegacyInfrastructure() {
        Set<Class<?>> dependencyTypes = Arrays.stream(DesignSpecForumService.class.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toSet());

        assertThat(dependencyTypes).contains(
                AuthService.class,
                MemberService.class,
                ResourceQueryService.class,
                com.resourcesharing.forum.service.resource.ResourceService.class,
                com.resourcesharing.forum.service.resource.FileService.class,
                RequestRewardService.class,
                InteractionService.class,
                ReportComplaintService.class,
                AppealService.class,
                AdminLogService.class,
                AdminCatalogService.class,
                AdminSystemService.class,
                AdminMemberService.class
        );
        assertThat(dependencyTypes)
                .doesNotContain(
                        LegacyDesignSpecForumService.class,
                        org.springframework.jdbc.core.JdbcTemplate.class,
                        org.springframework.transaction.PlatformTransactionManager.class,
                        com.resourcesharing.forum.service.point.PointManager.class,
                        com.resourcesharing.forum.domain.statemachine.ResourceStateMachine.class,
                        com.resourcesharing.forum.domain.statemachine.RequestStateMachine.class
                );
        assertThat(forumService).isNotNull();
        assertThat(legacyService).isNotNull();
    }

    @Test
    void migratedDomainServicesDoNotDependOnLegacyImplementation() {
        assertNoLegacyDependency(AuthService.class);
        assertNoLegacyDependency(MemberService.class);
        assertNoLegacyDependency(ResourceQueryService.class);
        assertNoLegacyDependency(com.resourcesharing.forum.service.resource.ResourceService.class);
        assertNoLegacyDependency(com.resourcesharing.forum.service.resource.FileService.class);
        assertNoLegacyDependency(InteractionService.class);
        assertNoLegacyDependency(RequestRewardService.class);
        assertNoLegacyDependency(AdminCatalogService.class);
        assertNoLegacyDependency(AdminSystemService.class);
        assertNoLegacyDependency(AdminMemberService.class);
        assertNoLegacyDependency(ReportComplaintService.class);
        assertNoLegacyDependency(AppealService.class);
        assertNoLegacyDependency(NotificationEventService.class);
        assertNoLegacyDependency(NotificationDispatcher.class);
        assertNoLegacyDependency(com.resourcesharing.forum.service.notification.NotificationService.class);
    }

    @Test
    void notificationFacadeAndDispatcherKeepInfrastructureBehindModuleBoundary() {
        assertNoFieldType(NotificationService.class, org.springframework.jdbc.core.JdbcTemplate.class);
        assertNoFieldType(NotificationDispatcher.class, org.springframework.jdbc.core.JdbcTemplate.class);
    }

    private static void assertNoLegacyDependency(Class<?> serviceType) {
        Set<Class<?>> fieldTypes = Arrays.stream(serviceType.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toSet());
        Set<Class<?>> constructorTypes = Arrays.stream(serviceType.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .collect(Collectors.toSet());

        assertThat(fieldTypes).doesNotContain(LegacyDesignSpecForumService.class);
        assertThat(constructorTypes).doesNotContain(LegacyDesignSpecForumService.class);
    }

    private static void assertNoFieldType(Class<?> serviceType, Class<?> forbiddenType) {
        Set<Class<?>> fieldTypes = Arrays.stream(serviceType.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toSet());

        assertThat(fieldTypes).doesNotContain(forbiddenType);
    }
}
