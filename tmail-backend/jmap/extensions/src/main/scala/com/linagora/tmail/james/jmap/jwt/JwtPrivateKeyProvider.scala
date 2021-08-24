package com.linagora.tmail.james.jmap.jwt

import org.apache.james.jwt.MissingOrInvalidKeyException

import java.security.PrivateKey

class JwtPrivateKeyProvider(jwtPrivateKeyConfiguration: JwtPrivateKeyConfiguration) {
  def get(): PrivateKey =
    JwtPrivateKeyConfiguration.fromPEM(jwtPrivateKeyConfiguration.jwtPrivateKey)
      .getOrElse(throw new MissingOrInvalidKeyException())
}