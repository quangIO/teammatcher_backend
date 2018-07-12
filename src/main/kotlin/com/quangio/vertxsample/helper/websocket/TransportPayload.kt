package com.quangio.vertxsample.helper.websocket

import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject

data class TransportPayload<out T>(val path: String, val data: T) {
  fun encodePrettily(): String = Json.encodePrettily(this)
}
