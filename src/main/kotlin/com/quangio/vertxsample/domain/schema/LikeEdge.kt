package com.quangio.vertxsample.domain.schema

import io.requery.*


@Entity
interface LikeEdge : Persistable {
  @get:Key
  @get:ForeignKey(references = Team::class)
  var fromTeam: Int

  @get:Key
  @get:ForeignKey(references = Team::class)
  var toTeam: Int
}
