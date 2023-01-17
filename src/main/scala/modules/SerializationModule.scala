package modules

import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.google.inject.{AbstractModule, Provides, Singleton}
import com.google.inject.name.Named
import com.twitter.finatra.jackson.ScalaObjectMapper
import net.codingwell.scalaguice.ScalaModule

class SerializationModule extends AbstractModule with ScalaModule {
  @Singleton
  @Provides
  @Named("SnakeCaseObjectMapper")
  def providesSnakeCaseObjectMapper(): ScalaObjectMapper = {
    ScalaObjectMapper.builder
      .withPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
      .objectMapper
  }

  @Singleton
  @Provides
  @Named("CamelCaseObjectMapper")
  def providesObjectMapper(): ScalaObjectMapper = {
    ScalaObjectMapper.builder
      .withPropertyNamingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
      .objectMapper
  }
}
