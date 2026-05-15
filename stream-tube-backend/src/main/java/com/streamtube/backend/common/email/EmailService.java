package com.streamtube.backend.common.email;

public interface EmailService {

  void sendEmailConfirmation(String to, String name, String confirmationLink);

  void sendPasswordReset(String to, String name, String resetLink);
}
