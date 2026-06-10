package com.resourcesharing.forum;

import com.resourcesharing.forum.service.notification.NotificationDispatcher;
import com.resourcesharing.forum.service.notification.NotificationEventService;
import com.resourcesharing.forum.service.notification.NotificationService;
import com.resourcesharing.forum.service.support.TxSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDispatcherTest {
    @Test
    void dispatchToMemberCreatesPendingNoticeAndMarksSentForSameEvent() {
        List<String> calls = new ArrayList<>();
        RecordingNotificationService notificationService = new RecordingNotificationService(calls);
        RecordingNotificationEventService eventService = new RecordingNotificationEventService(calls, 42L);
        NotificationDispatcher dispatcher = new NotificationDispatcher(notificationService, eventService, new InlineTxSupport());

        dispatcher.dispatchToMember(9L, "RESOURCE_APPROVED", "approved", "resource approved", "RESOURCE", 7L);

        assertThat(calls).containsExactly(
                "createPending:RESOURCE_APPROVED:RESOURCE:7:9:approved:resource approved",
                "createNoticeFromEvent:42:9:RESOURCE_APPROVED:approved:resource approved:RESOURCE:7",
                "markSent:42"
        );
    }

    @Test
    void dispatchToMemberMarksExistingPendingEventFailedAndCreatesFallbackNoticeWhenNoticeCreationFails() {
        List<String> calls = new ArrayList<>();
        RecordingNotificationService notificationService = new RecordingNotificationService(calls);
        notificationService.failNextNoticeCreations = 1;
        RecordingNotificationEventService eventService = new RecordingNotificationEventService(calls, 42L);
        NotificationDispatcher dispatcher = new NotificationDispatcher(notificationService, eventService, new InlineTxSupport());

        dispatcher.dispatchToMember(9L, "COMMENT", "reply", "new reply", "RESOURCE", 7L);

        assertThat(calls).containsExactly(
                "createPending:COMMENT:RESOURCE:7:9:reply:new reply",
                "createNoticeFromEvent:42:9:COMMENT:reply:new reply:RESOURCE:7",
                "markFailed:42:insert notice failed",
                "createNoticeFromEvent:null:9:COMMENT:reply:new reply:RESOURCE:7"
        );
    }

    @Test
    void dispatchToMemberRecordsFailureAndCreatesFallbackNoticeWhenPendingEventCannotBeCreated() {
        List<String> calls = new ArrayList<>();
        RecordingNotificationService notificationService = new RecordingNotificationService(calls);
        RecordingNotificationEventService eventService = new RecordingNotificationEventService(calls, 42L);
        eventService.failPendingCreation = true;
        NotificationDispatcher dispatcher = new NotificationDispatcher(notificationService, eventService, new InlineTxSupport());

        dispatcher.dispatchToMember(9L, "APPEAL_RESULT", "appeal", "appeal result", "APPEAL", 7L);

        assertThat(calls).containsExactly(
                "createPending:APPEAL_RESULT:APPEAL:7:9:appeal:appeal result",
                "recordFailure:APPEAL_RESULT:APPEAL:7:9:appeal:appeal result:insert event failed",
                "createNoticeFromEvent:null:9:APPEAL_RESULT:appeal:appeal result:APPEAL:7"
        );
    }

    private static class InlineTxSupport extends TxSupport {
        InlineTxSupport() {
            super(emptyProvider(), emptyProvider());
        }

        @Override
        public <T> T requiresNew(Supplier<T> action) {
            return action.get();
        }

        @SuppressWarnings("unchecked")
        private static <T> ObjectProvider<T> emptyProvider() {
            return new ObjectProvider<>() {
                @Override
                public T getObject(Object... args) throws BeansException {
                    return null;
                }

                @Override
                public T getIfAvailable() throws BeansException {
                    return null;
                }

                @Override
                public T getIfUnique() throws BeansException {
                    return null;
                }

                @Override
                public T getObject() throws BeansException {
                    return null;
                }
            };
        }
    }

    private static class RecordingNotificationService extends NotificationService {
        private final List<String> calls;
        private int failNextNoticeCreations;

        RecordingNotificationService(List<String> calls) {
            super(null, null, null);
            this.calls = calls;
        }

        @Override
        public void createNoticeFromEvent(Long eventId, Long receiverMemberId, String type, String title, String content, String targetType, Long targetId) {
            calls.add("createNoticeFromEvent:" + eventId + ":" + receiverMemberId + ":" + type + ":" + title + ":" + content + ":" + targetType + ":" + targetId);
            if (failNextNoticeCreations > 0) {
                failNextNoticeCreations--;
                throw new RuntimeException("insert notice failed");
            }
        }
    }

    private static class RecordingNotificationEventService extends NotificationEventService {
        private final List<String> calls;
        private final Long eventId;
        private boolean failPendingCreation;

        RecordingNotificationEventService(List<String> calls, Long eventId) {
            super(new InlineTxSupport());
            this.calls = calls;
            this.eventId = eventId;
        }

        @Override
        public Long createPending(String type, String targetType, Long targetId, Long receiverId, String title, String content) {
            calls.add("createPending:" + type + ":" + targetType + ":" + targetId + ":" + receiverId + ":" + title + ":" + content);
            if (failPendingCreation) {
                throw new RuntimeException("insert event failed");
            }
            return eventId;
        }

        @Override
        public void markSent(Long eventId) {
            calls.add("markSent:" + eventId);
        }

        @Override
        public void markFailed(Long eventId, String reason) {
            calls.add("markFailed:" + eventId + ":" + reason);
        }

        @Override
        public void recordFailure(String type, String targetType, Long targetId, Long receiverId, String title, String content, String reason) {
            calls.add("recordFailure:" + type + ":" + targetType + ":" + targetId + ":" + receiverId + ":" + title + ":" + content + ":" + reason);
        }
    }
}
