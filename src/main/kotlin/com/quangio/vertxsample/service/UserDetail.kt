package com.quangio.vertxsample.service

import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.AbstractUser
import io.vertx.ext.auth.AuthProvider
import io.vertx.kotlin.core.json.JsonObject

data class UserDetail(val id: Int, private val email: String): AbstractUser() {
  override fun doIsPermitted(permission: String?, resultHandler: Handler<AsyncResult<Boolean>>?) {
    TODO("not implemented")
  }

  override fun setAuthProvider(authProvider: AuthProvider?) {
    TODO("not implemented")
  }

  override fun principal(): JsonObject = JsonObject("id" to id, "email" to email)
}
