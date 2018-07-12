package com.quangio.vertxsample.config

object SecurityConfig {
  val facebookKey
    get() = System.getenv("FACEBOOK_KEY")
  val facebookSecret
    get() = System.getenv("FACEBOOK_SECRET")
}
