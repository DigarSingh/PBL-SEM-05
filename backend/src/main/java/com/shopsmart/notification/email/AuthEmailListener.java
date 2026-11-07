package com.shopsmart.notification.email;

import com.shopsmart.auth.events.EmailVerifiedEvent;
import com.shopsmart.auth.events.PasswordResetRequestedEvent;
import com.shopsmart.auth.events.UserRegisteredEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Account e-mails, sent asynchronously once the triggering transaction has committed. */
@Component
public class AuthEmailListener {

    private final EmailService emailService;
    private final EmailTemplates templates;

    public AuthEmailListener(EmailService emailService, EmailTemplates templates) {
        this.emailService = emailService;
        this.templates = templates;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserRegistered(UserRegisteredEvent event) {
        emailService.send(templates.emailVerification(event.email(), event.fullName(), event.verificationToken()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailVerified(EmailVerifiedEvent event) {
        emailService.send(templates.welcome(event.email(), event.fullName()));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        emailService.send(templates.passwordReset(event.email(), event.fullName(), event.resetToken()));
    }
}
