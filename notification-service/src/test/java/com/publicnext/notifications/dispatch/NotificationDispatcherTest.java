package com.publicnext.notifications.dispatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock
    private EmailNotificationGateway emailGateway;

    @Mock
    private SmsNotificationGateway smsGateway;

    @InjectMocks
    private NotificationDispatcher dispatcher;

    private final NotificationRequest request = new NotificationRequest(
            "order-1", "customer-1", "subject", "message");

    @Test
    void emailSucceeds_returnsEmail_andDoesNotCallSms() {
        NotificationDispatcher.DispatchOutcome outcome = dispatcher.dispatch(request);

        assertThat(outcome).isEqualTo(NotificationDispatcher.DispatchOutcome.EMAIL);
        verify(emailGateway).send(request);
        verify(smsGateway, never()).send(request);
    }

    @Test
    void emailFails_smsSucceeds_returnsSms() {
        doThrow(new NotificationDeliveryException("smtp down")).when(emailGateway).send(request);

        NotificationDispatcher.DispatchOutcome outcome = dispatcher.dispatch(request);

        assertThat(outcome).isEqualTo(NotificationDispatcher.DispatchOutcome.SMS);
        verify(emailGateway).send(request);
        verify(smsGateway).send(request);
    }

    @Test
    void emailFails_smsFails_returnsFailed() {
        doThrow(new NotificationDeliveryException("smtp down")).when(emailGateway).send(request);
        doThrow(new NotificationDeliveryException("twilio down")).when(smsGateway).send(request);

        NotificationDispatcher.DispatchOutcome outcome = dispatcher.dispatch(request);

        assertThat(outcome).isEqualTo(NotificationDispatcher.DispatchOutcome.FAILED);
        verify(emailGateway).send(request);
        verify(smsGateway).send(request);
    }
}
