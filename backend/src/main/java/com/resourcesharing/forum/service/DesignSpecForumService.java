package com.resourcesharing.forum.service;

import com.resourcesharing.forum.common.PageResult;
import com.resourcesharing.forum.service.audit.AppealService;
import com.resourcesharing.forum.service.audit.ReportComplaintService;
import com.resourcesharing.forum.service.identity.AuthService;
import com.resourcesharing.forum.service.identity.MemberService;
import com.resourcesharing.forum.service.interaction.InteractionService;
import com.resourcesharing.forum.service.request.RequestRewardService;
import com.resourcesharing.forum.service.resource.ResourceQueryService;
import com.resourcesharing.forum.service.system.AdminCatalogService;
import com.resourcesharing.forum.service.system.AdminLogService;
import com.resourcesharing.forum.service.system.AdminMemberService;
import com.resourcesharing.forum.service.system.AdminSystemService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class DesignSpecForumService {
    private final AuthService authService;
    private final MemberService memberService;
    private final ResourceQueryService resourceQueryService;
    private final com.resourcesharing.forum.service.resource.ResourceService resourceService;
    private final com.resourcesharing.forum.service.resource.FileService fileService;
    private final RequestRewardService requestRewardService;
    private final InteractionService interactionService;
    private final ReportComplaintService reportComplaintService;
    private final AppealService appealService;
    private final AdminLogService adminLogService;
    private final AdminCatalogService adminCatalogService;
    private final AdminSystemService adminSystemService;
    private final AdminMemberService adminMemberService;

    public DesignSpecForumService(
            AuthService authService,
            MemberService memberService,
            ResourceQueryService resourceQueryService,
            com.resourcesharing.forum.service.resource.ResourceService resourceService,
            com.resourcesharing.forum.service.resource.FileService fileService,
            RequestRewardService requestRewardService,
            InteractionService interactionService,
            ReportComplaintService reportComplaintService,
            AppealService appealService,
            AdminLogService adminLogService,
            AdminCatalogService adminCatalogService,
            AdminSystemService adminSystemService,
            AdminMemberService adminMemberService
    ) {
        this.authService = authService;
        this.memberService = memberService;
        this.resourceQueryService = resourceQueryService;
        this.resourceService = resourceService;
        this.fileService = fileService;
        this.requestRewardService = requestRewardService;
        this.interactionService = interactionService;
        this.reportComplaintService = reportComplaintService;
        this.appealService = appealService;
        this.adminLogService = adminLogService;
        this.adminCatalogService = adminCatalogService;
        this.adminSystemService = adminSystemService;
        this.adminMemberService = adminMemberService;
    }

    public Map<String, Object> login(Map<String, Object> request) {
        return authService.login(request);
    }

    public Map<String, Object> register(Map<String, Object> request) {
        return authService.register(request);
    }

    public Map<String, Object> userProfile(Long accountId) {
        return memberService.userProfile(accountId);
    }

    public Map<String, Object> updateUserProfile(Long accountId, Map<String, Object> request) {
        return memberService.updateUserProfile(accountId, request);
    }

    public Map<String, Object> changePassword(Long accountId, Map<String, Object> request) {
        return memberService.changePassword(accountId, request);
    }

    public Map<String, Object> changeEmail(Long accountId, Map<String, Object> request) {
        return memberService.changeEmail(accountId, request);
    }

    public Map<String, Object> requestResetPasswordCode(Map<String, Object> request) {
        return authService.requestResetPasswordCode(request);
    }

    public Map<String, Object> resetPassword(Map<String, Object> request) {
        return authService.resetPassword(request);
    }

    public PageResult<Map<String, Object>> listResources(Map<String, String> params, Long accountId) {
        return resourceQueryService.listResources(params, accountId);
    }

    public Map<String, Object> publishResource(Long accountId, Map<String, Object> request, List<MultipartFile> files) {
        return resourceService.publishResource(accountId, request, files);
    }

    public Map<String, Object> resourceDetail(Long resourceId, Long accountId) {
        return resourceQueryService.resourceDetail(resourceId, accountId);
    }

    public void deleteResource(Long resourceId, Long accountId) {
        resourceService.deleteResource(resourceId, accountId);
    }

    public Map<String, Object> auditResource(Long resourceId, Long adminAccountId, Map<String, Object> request) {
        return resourceService.auditResource(resourceId, adminAccountId, request);
    }

    public PageResult<Map<String, Object>> adminListResources(Map<String, String> params, Long adminAccountId) {
        return resourceQueryService.adminListResources(params, adminAccountId);
    }

    public Map<String, Object> transitionResourceByAdmin(Long resourceId, Long adminAccountId, String action, Map<String, Object> request) {
        return resourceService.transitionResourceByAdmin(resourceId, adminAccountId, action, request);
    }

    public Map<String, Object> submitResource(Long resourceId, Long accountId) {
        return resourceService.submitResource(resourceId, accountId);
    }

    public Map<String, Object> withdrawResource(Long resourceId, Long accountId, Map<String, Object> request) {
        return resourceService.withdrawResource(resourceId, accountId, request);
    }

    public Map<String, Object> toggleResourceInteraction(Long resourceId, String action, Long accountId) {
        return interactionService.toggleResourceInteraction(resourceId, action, accountId);
    }

    public Map<String, Object> rateResource(Long resourceId, Long accountId, Map<String, Object> request) {
        return interactionService.rateResource(resourceId, accountId, request);
    }

    public Map<String, Object> downloadAttachment(Long attachmentId, Long accountId) {
        return fileService.downloadAttachment(attachmentId, accountId);
    }

    public PageResult<Map<String, Object>> listRequests(Map<String, String> params) {
        return requestRewardService.listRequests(params);
    }

    public Map<String, Object> createRequest(Long accountId, Map<String, Object> request) {
        return requestRewardService.createRequest(accountId, request);
    }

    public Map<String, Object> requestDetail(Long requestId, Long accountId) {
        return requestRewardService.requestDetail(requestId, accountId);
    }

    public void cancelRequest(Long requestId, Long accountId, Map<String, Object> request) {
        requestRewardService.cancelRequest(requestId, accountId, request);
    }

    public PageResult<Map<String, Object>> listReplies(Long requestId, Map<String, String> params) {
        return requestRewardService.listReplies(requestId, params);
    }

    public Map<String, Object> replyRequest(Long requestId, Long accountId, Map<String, Object> request) {
        return requestRewardService.replyRequest(requestId, accountId, request);
    }

    public Map<String, Object> settleRequest(Long requestId, Long accountId, Map<String, Object> request) {
        return requestRewardService.settleRequest(requestId, accountId, request);
    }

    public PageResult<Map<String, Object>> listComments(Map<String, String> params, Long accountId) {
        return interactionService.listComments(params, accountId);
    }

    public Map<String, Object> addComment(Long accountId, Map<String, Object> request) {
        return interactionService.addComment(accountId, request);
    }

    public Map<String, Object> addComment(String targetType, Long targetId, String content, Long accountId) {
        return interactionService.addComment(targetType, targetId, content, accountId);
    }

    public Map<String, Object> commentDetail(Long commentId, Long accountId) {
        return interactionService.commentDetail(commentId, accountId);
    }

    public Map<String, Object> updateComment(Long commentId, Long accountId, Map<String, Object> request) {
        return interactionService.updateComment(commentId, accountId, request);
    }

    public void deleteComment(Long commentId, Long accountId) {
        interactionService.deleteComment(commentId, accountId);
    }

    public Map<String, Object> likeComment(Long commentId, Long accountId) {
        return interactionService.likeComment(commentId, accountId);
    }

    public Map<String, Object> report(Long accountId, Map<String, Object> request) {
        return reportComplaintService.report(accountId, request);
    }

    public Map<String, Object> appeal(Long accountId, Map<String, Object> request) {
        return appealService.appeal(accountId, request);
    }

    public Map<String, Object> handleReport(Long reportId, Long adminAccountId, Map<String, Object> request) {
        return reportComplaintService.handleReport(reportId, adminAccountId, request);
    }

    public Map<String, Object> handleAppeal(Long appealId, Long adminAccountId, Map<String, Object> request) {
        return appealService.handleAppeal(appealId, adminAccountId, request);
    }

    public PageResult<Map<String, Object>> adminLogs(Map<String, String> params) {
        return adminLogService.adminLogs(params);
    }

    public Map<String, Object> disableMember(Long adminAccountId, Long memberId, Map<String, Object> request) {
        return adminMemberService.disableMember(adminAccountId, memberId, request);
    }

    public Map<String, Object> enableMember(Long adminAccountId, Long memberId) {
        return adminMemberService.enableMember(adminAccountId, memberId);
    }

    public PageResult<Map<String, Object>> listCategories(Map<String, String> params) {
        return adminCatalogService.listCategories(params);
    }

    public Map<String, Object> createCategory(Long adminAccountId, Map<String, Object> request) {
        return adminCatalogService.createCategory(adminAccountId, request);
    }

    public Map<String, Object> updateCategory(Long adminAccountId, Long categoryId, Map<String, Object> request) {
        return adminCatalogService.updateCategory(adminAccountId, categoryId, request);
    }

    public Map<String, Object> disableCategory(Long adminAccountId, Long categoryId) {
        return adminCatalogService.disableCategory(adminAccountId, categoryId);
    }

    public PageResult<Map<String, Object>> listTags(Map<String, String> params) {
        return adminCatalogService.listTags(params);
    }

    public Map<String, Object> createTag(Long adminAccountId, Map<String, Object> request) {
        return adminCatalogService.createTag(adminAccountId, request);
    }

    public Map<String, Object> disableTag(Long adminAccountId, Long tagId) {
        return adminCatalogService.disableTag(adminAccountId, tagId);
    }

    public Map<String, Object> mergeTags(Long adminAccountId, Map<String, Object> request) {
        return adminCatalogService.mergeTags(adminAccountId, request);
    }

    public Map<String, Object> systemConfig() {
        return adminSystemService.systemConfig();
    }

    public Map<String, Object> updateSystemConfig(Long adminAccountId, Map<String, Object> request) {
        return adminSystemService.updateSystemConfig(adminAccountId, request);
    }

    public Map<String, Object> refreshCache(Long adminAccountId) {
        return adminSystemService.refreshCache(adminAccountId);
    }

    public Map<String, Object> closeRequestByAdmin(Long adminAccountId, Long requestId, Map<String, Object> request) {
        return requestRewardService.closeRequestByAdmin(adminAccountId, requestId, request);
    }

    public Map<String, Object> deleteReplyByAdmin(Long adminAccountId, Long replyId) {
        return requestRewardService.deleteReplyByAdmin(adminAccountId, replyId);
    }
}
