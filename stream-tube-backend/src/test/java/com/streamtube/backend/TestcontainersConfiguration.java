package com.streamtube.backend;

import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgresContainer() {
    return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
  }

  @Bean
  @Primary
  JavaMailSender javaMailSender() {
    JavaMailSenderImpl delegate = new JavaMailSenderImpl();
    return new JavaMailSender() {
      @Override
      public MimeMessage createMimeMessage() {
        return delegate.createMimeMessage();
      }

      @Override
      public MimeMessage createMimeMessage(InputStream contentStream) throws MailException {
        return delegate.createMimeMessage(contentStream);
      }

      @Override
      public void send(MimeMessage mimeMessage) throws MailException {}

      @Override
      public void send(MimeMessage... mimeMessages) throws MailException {}

      @Override
      public void send(SimpleMailMessage simpleMessage) throws MailException {}

      @Override
      public void send(SimpleMailMessage... simpleMessages) throws MailException {}
    };
  }
}
