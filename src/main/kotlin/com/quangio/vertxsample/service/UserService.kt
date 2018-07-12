package com.quangio.vertxsample.service

import io.vertx.ext.web.Session
import io.vertx.ext.web.handler.impl.UserHolder
import io.vertx.ext.web.sstore.SessionStore
import io.vertx.kotlin.coroutines.awaitResult
import kotlinx.coroutines.experimental.runBlocking
import org.slf4j.LoggerFactory

class UserService(private val sessionStore: SessionStore) {
  private val logger = LoggerFactory.getLogger(this::class.java)

  fun getFromSession(sid: String): UserDetail {
    val session = runBlocking {
       awaitResult<Session> { sessionStore[sid, it] }
    }
    return (session.data()["__vertx.userHolder"] as UserHolder).context.user() as UserDetail
  }
}
