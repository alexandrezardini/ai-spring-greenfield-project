package com.streamtube.backend.common.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

class EmailTemplateRenderTest {

  private static SpringTemplateEngine engine;

  @BeforeAll
  static void setUpEngine() {
    ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
    resolver.setPrefix("templates/");
    resolver.setSuffix(".html");
    resolver.setTemplateMode(TemplateMode.HTML);
    resolver.setCharacterEncoding("UTF-8");
    resolver.setOrder(1);

    engine = new SpringTemplateEngine();
    engine.setTemplateResolver(resolver);
  }

  @Test
  void emailConfirmationTemplate_rendersNameAndLink() {
    Context ctx = new Context();
    ctx.setVariable("name", "Alice");
    ctx.setVariable("confirmationLink", "http://localhost:3000/auth/confirm-email?token=TOKEN1");

    String html = engine.process("email/email-confirmation", ctx);

    assertThat(html).isNotEmpty();
    assertThat(html).contains("Alice");
    assertThat(html).contains("TOKEN1");
    assertThat(html).doesNotContain("null");
  }

  @Test
  void passwordResetTemplate_rendersNameAndLink() {
    Context ctx = new Context();
    ctx.setVariable("name", "Bob");
    ctx.setVariable("resetLink", "http://localhost:3000/auth/reset-password?token=TOKEN2");

    String html = engine.process("email/password-reset", ctx);

    assertThat(html).isNotEmpty();
    assertThat(html).contains("Bob");
    assertThat(html).contains("TOKEN2");
    assertThat(html).doesNotContain("null");
  }
}
