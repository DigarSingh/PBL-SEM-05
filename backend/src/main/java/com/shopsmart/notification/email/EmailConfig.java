package com.shopsmart.notification.email;

import com.shopsmart.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

@Configuration
public class EmailConfig {

    private static final Logger log = LoggerFactory.getLogger(EmailConfig.class);

    @Bean
    EmailSender emailSender(ObjectProvider<JavaMailSender> mailSender, AppProperties properties,
                            @Value("${spring.mail.host:}") String host) {
        JavaMailSender javaMailSender = mailSender.getIfAvailable();
        if (StringUtils.hasText(host) && javaMailSender != null) {
            log.info("E-mail delivery via SMTP host {}", host);
            return new SmtpEmailSender(javaMailSender, properties.mail().from(), properties.mail().fromName());
        }
        log.warn("MAIL_HOST is not set: e-mails will be logged (without content) instead of sent");
        return new LoggingEmailSender();
    }
}
