package com.resourcesharing.forum.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    public List<Map<String, Object>> list() {
        return List.of();
    }

    public int unreadCount() {
        return 0;
    }

    public void read(Long id) {
    }
}

