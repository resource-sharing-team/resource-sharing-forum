package com.resourcesharing.forum.service.point;

public interface PointManager {

    void freezeForRequest(Long memberId, Integer points, Long requestId);

    void refundRequest(Long requestId);

    void transferReward(Long requestId, Long winnerMemberId);

    Long recordEvent(
            Long memberId,
            String type,
            Integer amount,
            Integer afterBalance,
            String scene,
            Long relatedTargetId,
            String remark
    );
}
