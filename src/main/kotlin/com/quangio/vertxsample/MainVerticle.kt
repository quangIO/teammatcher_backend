package com.quangio.vertxsample

import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.quangio.vertxsample.config.SecurityConfig
import com.quangio.vertxsample.domain.schema.*
import com.quangio.vertxsample.helper.ResponseWithCode
import com.quangio.vertxsample.helper.endWithJson
import com.quangio.vertxsample.helper.toPage
import com.quangio.vertxsample.helper.websocket.TransportPayload
import com.quangio.vertxsample.service.UserDetail
import com.quangio.vertxsample.service.UserService
import io.requery.Persistable
import io.requery.cache.EmptyEntityCache
import io.requery.kotlin.eq
import io.requery.kotlin.gt
import io.requery.kotlin.set
import io.requery.sql.KotlinConfiguration
import io.requery.sql.KotlinEntityDataStore
import io.requery.sql.SchemaModifier
import io.requery.sql.TableCreationMode
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.ServerWebSocket
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.oauth2.AccessToken
import io.vertx.ext.auth.oauth2.providers.FacebookAuth
import io.vertx.ext.jdbc.spi.impl.HikariCPDataSourceProvider
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.*
import io.vertx.ext.web.sstore.LocalSessionStore
import io.vertx.kotlin.core.json.JsonObject
import io.vertx.kotlin.core.json.get
import io.vertx.kotlin.coroutines.CoroutineVerticle
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory


class MainVerticle : CoroutineVerticle() {
  private val oauth2 by lazy { FacebookAuth.create(vertx, SecurityConfig.facebookKey, SecurityConfig.facebookSecret) }
  private val sessionStore by lazy { LocalSessionStore.create(vertx) }
  private val userService by lazy { UserService(sessionStore) }

  private val logger = LoggerFactory.getLogger(this::class.java)

  private val FRONTEND_BASE_URL = System.getenv("FRONTEND_BASE_URL")
  private val BASE_URL = System.getenv("BASE_URL")
  private val JDBC_DATABASE_URL = System.getenv("JDBC_DATABASE_URL")

  private val data: KotlinEntityDataStore<Persistable> by lazy {
    val config = JsonObject()
      // .put("jdbcUrl", "jdbc:postgresql://localhost:5432/shecodes_test")
      .put("jdbcUrl", JDBC_DATABASE_URL)
      // .put("driverClassName", "org.postgresql.jdbcDriver")
      .put("maximumPoolSize", 10)
    val source = HikariCPDataSourceProvider().getDataSource(config)
    SchemaModifier(source, Models.DEFAULT).createTables(TableCreationMode.CREATE_NOT_EXISTS)
    KotlinEntityDataStore<Persistable>(KotlinConfiguration(Models.DEFAULT, source, cache = EmptyEntityCache(), useDefaultLogging = false))
  }


  override fun start(startFuture: Future<Void>?) {
    val router = createRouter()

    val port = System.getenv("PORT")?.toInt() ?: 8080


    Json.mapper.registerModule(KotlinModule())

    vertx.createHttpServer().apply {
      requestHandler(router::accept)
      websocketHandler(webSocketHandler)
    }.listen(port) {
      if (it.failed()) {
        logger.error(it.cause().message)
      } else {
        logger.info("Server started at port $port")
      }
    }
  }


  private val webSocketHandler = Handler<ServerWebSocket> { ws ->
    val user = try {
      userService.getFromSession(ws.query())
    } catch (e: Exception) {
      logger.warn(MarkerFactory.getMarker("InvalidSession"), "${ws.query()} is not a valid session id")
      ws.reject()
      return@Handler
    }
  }

  private fun createRouter() = Router.router(vertx).apply {
    route().handler(CookieHandler.create())
    route().handler(SessionHandler.create(sessionStore))
    route().handler(UserSessionHandler.create(oauth2))
    route().handler(BodyHandler.create())
    // route().handler(CorsHandler.create("*"))
    route().handler(CorsHandler.create(FRONTEND_BASE_URL.removeSuffix("/#"))
      .allowCredentials(true)
      .allowedHeader("content-type")
    )
    // route().handler(CorsHandler.create(FRONTEND_BASE_URL).allowCredentials(true).allowedHeader("content-type"))


    get("/login").handler(
      OAuth2AuthHandler.create(oauth2, BASE_URL)
        .setupCallback(route("/callback").handler(callbackTokenHandler))
        .addAuthorities(setOf("email"))
    )

    get("/login").handler { context ->
      (context.user() as UserDetail).id
      toPage(context, FRONTEND_BASE_URL)
    }

    get("/logout").handler { context ->
      context.clearUser()
      toPage(context, "http://devel.faith")
    }

    get("/users").handler { context->
      context.response().endWithJson(data.select(Person::class).get().toList())
    }

    get("/teams").handler { context ->
      context.response().endWithJson(data.select(Team::class).where(Team::size gt 0).get().toList())
    }

    get("/receives").handler { context ->
      val userId = try {
        (context.user() as UserDetail).id
      } catch (e: Exception) {
        context.response().setStatusCode(401).endWithJson(ResponseWithCode(401, "unauthorized"))
        return@handler
      }
      val teams = data.withTransaction {
        val userTeam = select(Person::class).where(Person::id eq userId).get().first().team
        select(LikeEdge::class).where(LikeEdge::toTeam eq userTeam.id).get()
      }.map { it.fromTeam }
      context.response().endWithJson(ResponseWithCode(200, teams))
    }

    get("/sends").handler { context ->
      val userId = try {
        (context.user() as UserDetail).id
      } catch (e: Exception) {
        context.response().setStatusCode(401).endWithJson(ResponseWithCode(401, "unauthorized"))
        return@handler
      }
      val teams = data.withTransaction {
        val userTeam = select(Person::class).where(Person::id eq userId).get().first().team
        select(LikeEdge::class).where(LikeEdge::fromTeam eq userTeam.id).get()
      }.map { it.toTeam }
      context.response().endWithJson(ResponseWithCode(200, teams))
    }

    get("/me").handler { context ->
      try {
        val userId = (context.user() as UserDetail).id
        val me = data.select(Person::class).where(Person::id eq userId).get().first()
        context.response().endWithJson(ResponseWithCode(200, me))
      } catch (e: Exception) {
        context.response().endWithJson(ResponseWithCode(401, "Login with facebook to continue"))
      }
    }

    post("/delete").handler { context ->
      val userId = try {
        (context.user() as UserDetail).id
      } catch (e: Exception) {
        context.response().setStatusCode(401).endWithJson(ResponseWithCode(401, "unauthorized"))
        return@handler
      }
      val email = context.bodyAsJson.getString("email")

      data.withTransaction {
        val user = select(Person::class).where((Person::id eq userId) and (Person::email eq email)).get().first()
        user.team.size--
        if (user.team.size == 0) {
          delete(user.team)
        } else {
          update(user.team)
          delete(Person::class).where(Person::id eq user.id).get().value()
        }
        1
      }
      context.clearUser()
      context.response().endWithJson(ResponseWithCode(200))
    }

    post("/update").handler(updateProfile)
    post("/like").handler(likeGroup)
  }

  private val updateProfile = Handler<RoutingContext> { context ->
    val body = context.bodyAsJson
    logger.trace(body.toString())
    val userId = (context.user() as UserDetail).id

    data.update(Person::class)
      .set(Person::name, body.getString("name"))
      .set(Person::intro, body.getString("intro"))
      .set(Person::wantTeam, body.getString("wantTeam"))
      .where(Person::id eq userId).get().value()
    context.response().endWithJson(ResponseWithCode(200))
  }

  private val likeGroup = Handler<RoutingContext> { context ->
    val userId = try {
      (context.user() as UserDetail).id
    } catch (e: Exception) {
      context.response().setStatusCode(401).endWithJson(ResponseWithCode(401, "unauthorized"))
      return@Handler
    }
    logger.info("$userId request")
    val likeTeamId = context.bodyAsJson.getInteger("team")
    data.withTransaction {
      val userTeam = select(Person::class).where(Person::id eq userId).get().first().team
      val likeTeam = select(Team::class).where(Team::id eq likeTeamId).get().first()

      if (userTeam.size + likeTeam.size > 5 || userTeam.id == likeTeam.id) {
        context.response().endWithJson(ResponseWithCode(400, "Maximum team size is 5 and minimum is 1"))
        logger.warn("userId=$userId at teamId=${userTeam.id} cannot like teamId=$likeTeamId [${likeTeam.size} ${userTeam.size}]")
        return@withTransaction
      }

      val likeBack = select(LikeEdge::class).where(LikeEdge::toTeam.eq(userTeam.id) and LikeEdge::fromTeam.eq(likeTeamId)).get().firstOrNull()

      if (likeBack != null) {
        logger.info("new team is $userTeam")

        update(PersonEntity::class).set(PersonEntity.TEAM_ID, userTeam.id).where(PersonEntity.TEAM_ID.eq(likeTeamId)).get().value()
        delete(Team::class).where(Team::id eq likeTeam.id).get().value()
        userTeam.size += likeTeam.size
        update(userTeam)
      } else {
        logger.info("Create like edge ${userTeam.id} ${likeTeam.id}")
        insert(LikeEdgeEntity().apply {
          fromTeam = userTeam.id
          toTeam = likeTeam.id
        })
      }
    }

    if (!context.response().ended())
      context.response().endWithJson(ResponseWithCode(200))
  }

  private val callbackTokenHandler = Handler<RoutingContext> { context ->
    oauth2.authenticate(JsonObject("redirect_uri" to "$BASE_URL/callback", "code" to context.queryParam("code")[0])) { res ->
      if (res.failed()) {
        logger.warn("Oauth 2 error")
        context.response().end(res.cause().message)
      } else {
        val token = res.result() as AccessToken
        logger.trace(token.principal().toString())
        token.fetch("https://graph.facebook.com/v3.0/me?fields=id%2Cname%2Cemail") { it ->
          if (it.failed()) {
            logger.warn("Cannot fetch data")
            context.response().end(it.cause().message)
            return@fetch
          }
          val result = it.result().jsonObject()
          logger.trace(result.toString())

          val u = PersonEntity().apply {
            name = result["name"]
            email = result["email"]
            fbId = result.getString("id").toLong()
          }
          val user = data.withTransaction {
            var user = select(Person::class).where(Person::email eq u.email).limit(1).get().firstOrNull()
            if (user == null) {
                val team = insert(TeamEntity().apply { size = 1 })
                logger.info("Created $team")
                user = insert(u.apply { this.team = team })
              }
            user
          }
          context.setUser(UserDetail(user.id, u.email))
          // context.response().endWithJson(it.result().jsonObject())
          if (user.intro.isNullOrBlank()) {
            toPage(context, "$FRONTEND_BASE_URL/profile")
          } else {
            toPage(context, FRONTEND_BASE_URL)
          }
        }
      }
    }
  }
}

fun main(args: Array<String>) {
  val vertx = Vertx.vertx()
  vertx.deployVerticle(MainVerticle())
}
