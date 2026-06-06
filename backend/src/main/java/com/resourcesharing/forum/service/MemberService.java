package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageResult;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MemberService {
    private final com.resourcesharing.forum.service.identity.MemberService delegate;

    public MemberService(com.resourcesharing.forum.service.identity.MemberService delegate) {
        this.delegate = delegate;
    }

    public Map<String, Object> currentMember(Long accountId) {
        return delegate.userProfile(accountId);
    }

    public PageResult<Map<String, Object>> pointFlows(Long accountId) {
        return delegate.pointFlows(accountId, 1, 20);
    }
}
