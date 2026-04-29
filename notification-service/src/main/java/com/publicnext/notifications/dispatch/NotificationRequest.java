package com.publicnext.notifications.dispatch;

public record NotificationRequest(
        String orderId,
        String recipient,
        String subject,
        String message
) {
}
