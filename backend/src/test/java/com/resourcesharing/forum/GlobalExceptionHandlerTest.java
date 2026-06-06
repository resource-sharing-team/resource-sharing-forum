package com.resourcesharing.forum;

import com.resourcesharing.forum.common.ApiResponse;
import com.resourcesharing.forum.common.BusinessException;
import com.resourcesharing.forum.common.ErrorCode;
import com.resourcesharing.forum.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessExceptionsKeepWrapperAndAreLoggedAsWarn(CapturedOutput output) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new BusinessException(ErrorCode.FORBIDDEN, "operation denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.FORBIDDEN.code());
        assertThat(output).contains("Business exception handled");
        assertThat(output).contains("operation denied");
    }

    @Test
    void systemExceptionsKeepWrapperAndAreLoggedAsError(CapturedOutput output) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(
                new IllegalStateException("database connection failed"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_ERROR.code());
        assertThat(output).contains("System exception handled");
        assertThat(output).contains("database connection failed");
    }
}
