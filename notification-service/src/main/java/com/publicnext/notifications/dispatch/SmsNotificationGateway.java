package com.publicnext.notifications.dispatch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SmsNotificationGateway {

    public void send(NotificationRequest request) {
        log.info("[sms] to={} body=\"{}\" orderId={}",
                request.recipient(), request.message(), request.orderId());
    }
}
