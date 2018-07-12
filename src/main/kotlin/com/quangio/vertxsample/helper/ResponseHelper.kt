package com.quangio.vertxsample.helper

import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.Json
import io.vertx.ext.web.RoutingContext

fun HttpServerResponse.endWithJson(obj: Any) {
  this.putHeader("Content-Type", "application/json; charset=utf-8").end(Json.encodePrettily(obj))
}

fun toPage(context: RoutingContext, page: String = "http://localhost:8081/#/") =
  context.response().putHeader("location", page).setStatusCode(302).end()

data class ResponseWithCode(val code: Int = 200, val message: Any? = "OK")
