package com.streamtube.backend.common.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ExtendWith(MockitoExtension.class)
class ThymeleafEmailServiceTest {

  private JavaMailSender mailSender;
  private ThymeleafEmailService service;

  @BeforeEach
  void setUp() {
    mailSender = mock(JavaMailSender.class);
    JavaMailSenderImpl delegate = new JavaMailSenderImpl();
    when(mailSender.createMimeMessage()).thenAnswer(inv -> delegate.createMimeMessage());

    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setOrder(1);

    SpringTemplateEngine engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);

    service = new ThymeleafEmailService(mailSender, engine);
  }

  @Test
  void sendEmailConfirmation_invokesMailSenderOnce_withCorrectRecipientAndBody() throws Exception {
    service.sendEmailConfirmation(
        "alice@example.com", "Alice", "http://localhost:3000/auth/confirm-email?token=ABC123");

    ArgumentCaptor<MimeMessage> captor = forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());

    MimeMessage sent = captor.getValue();
    assertThat(sent.getAllRecipients()).hasSize(1);
    assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("alice@example.com");
    assertThat(sent.getSubject()).isEqualTo("Confirm your StreamTube account");
    assertThat(sent.getContent().toString()).contains("Alice").contains("ABC123");
  }

  @Test
  void sendPasswordReset_invokesMailSenderOnce_withCorrectRecipientAndBody() throws Exception {
    service.sendPasswordReset(
        "bob@example.com", "Bob", "http://localhost:3000/auth/reset-password?token=XYZ789");

    ArgumentCaptor<MimeMessage> captor = forClass(MimeMessage.class);
    verify(mailSender).send(captor.capture());

    MimeMessage sent = captor.getValue();
    assertThat(sent.getAllRecipients()).hasSize(1);
    assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("bob@example.com");
    assertThat(sent.getSubject()).isEqualTo("Reset your StreamTube password");
    assertThat(sent.getContent().toString()).contains("Bob").contains("XYZ789");
  }
}
