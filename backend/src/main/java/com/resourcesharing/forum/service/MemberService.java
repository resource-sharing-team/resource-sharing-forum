package com.resourcesharing.forum.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MemberService {

    public Map<String, Object> currentMember() {
        return Map.of(
                "levelName", "普通会员",
                "points", 650,
                "availablePoints", 650,
                "frozenPoints", 0,
                "dailyDownloadLimit", 10,
                "maxFilesPerResource", 5
        );
    }

    public Map<String, Object> pointFlows() {
        return Map.of("records", List.of(), "total", 0);
    }
}

