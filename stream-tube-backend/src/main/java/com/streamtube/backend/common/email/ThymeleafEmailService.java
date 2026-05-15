package com.streamtube.backend.common.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@EnableConfigurationProperties(AppProperties.class)
public class ThymeleafEmailService implements EmailService {

  private final JavaMailSender mailSender;
  private final SpringTemplateEngine templateEngine;

  public ThymeleafEmailService(JavaMailSender mailSender, SpringTemplateEngine templateEngine) {
    this.mailSender = mailSender;
    this.templateEngine = templateEngine;
  }

  @Override
  public void sendEmailConfirmation(String to, String name, String confirmationLink) {
    Context ctx = new Context();
    ctx.setVariable("name", name);
    ctx.setVariable("confirmationLink", confirmationLink);
    String body = templateEngine.process("email/email-confirmation", ctx);
    sendHtml(to, "Confirm your StreamTube account", body);
  }

  @Override
  public void sendPasswordReset(String to, String name, String resetLink) {
    Context ctx = new Context();
    ctx.setVariable("name", name);
    ctx.setVariable("resetLink", resetLink);
    String body = templateEngine.process("email/password-reset", ctx);
    sendHtml(to, "Reset your StreamTube password", body);
  }

  private void sendHtml(String to, String subject, String body) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(body, true);
    } catch (MessagingException e) {
      throw new MailParseException("Failed to compose email to " + to, e);
    }
    mailSender.send(message);
  }
}
