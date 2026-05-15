package com.streamtube.backend.common.persistence;

import java.util.List;
import org.postgresql.util.PGobject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions;

@Configuration
public class DataJdbcConfig {

  @Bean
  JdbcCustomConversions jdbcCustomConversions() {
    return new JdbcCustomConversions(List.of(PGobjectToStringConverter.INSTANCE));
  }

  @ReadingConverter
  enum PGobjectToStringConverter implements Converter<PGobject, String> {
    INSTANCE;

    @Override
    public String convert(PGobject source) {
      return source.getValue();
    }
  }
}
