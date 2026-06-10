package com.resourcesharing.forum.service.point;

public interface PointManager {

    void freezeForRequest(Long memberId, Integer points, Long requestId);

    void refundRequest(Long requestId);

    void transferReward(Long requestId, Long winnerMemberId);

    void collectFrozenRequest(Long requestId, Long operatorId, String description);

    void restoreCollectedRequest(Long requestId, Long operatorId, String description);

    boolean earn(
            Long memberId,
            Integer points,
            String scene,
            String relatedType,
            Long relatedId,
            Long operatorId,
            String description,
            String bizKey
    );

    boolean deduct(
            Long memberId,
            Integer points,
            String scene,
            String relatedType,
            Long relatedId,
            Long operatorId,
            String description,
            String bizKey
    );

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
