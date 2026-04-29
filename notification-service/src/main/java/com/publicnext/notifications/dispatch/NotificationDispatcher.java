package com.publicnext.notifications.dispatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final EmailNotificationGateway emailGateway;
    private final SmsNotificationGateway smsGateway;

    public DispatchOutcome dispatch(NotificationRequest request) {
        try {
            emailGateway.send(request);
            return DispatchOutcome.EMAIL;
        } catch (Exception emailFailure) {
            log.warn("Email path failed for order {} — falling back to SMS: {} ({})",
                    request.orderId(), emailFailure.getClass().getSimpleName(), emailFailure.getMessage());
        }
        try {
            smsGateway.send(request);
            return DispatchOutcome.SMS;
        } catch (NotificationDeliveryException smsFailure) {
            log.error("Both email and SMS failed for order {}: {}",
                    request.orderId(), smsFailure.getMessage());
            return DispatchOutcome.FAILED;
        }
    }

    public enum DispatchOutcome { EMAIL, SMS, FAILED }
}
