package com.github.pepek42.asteroids

import spock.lang.Specification
import spock.lang.Subject

class AsteroidsCoopTest extends Specification {
  @Subject
  def asteroidsCoop = new AsteroidsCoop()

  def "asteroids coop should exist"() {
    expect:
      1 == 1
  }
}
