package com.resourcesharing.forum.service.identity;

import com.resourcesharing.forum.service.LegacyDesignSpecForumService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("designSpecMemberService")
public class MemberService {
    private final LegacyDesignSpecForumService legacy;

    public MemberService(LegacyDesignSpecForumService legacy) {
        this.legacy = legacy;
    }

    public Map<String, Object> userProfile(Long accountId) {
        return legacy.userProfile(accountId);
    }

    public Map<String, Object> updateUserProfile(Long accountId, Map<String, Object> request) {
        return legacy.updateUserProfile(accountId, request);
    }

    public Map<String, Object> changePassword(Long accountId, Map<String, Object> request) {
        return legacy.changePassword(accountId, request);
    }

    public Map<String, Object> changeEmail(Long accountId, Map<String, Object> request) {
        return legacy.changeEmail(accountId, request);
    }
}
