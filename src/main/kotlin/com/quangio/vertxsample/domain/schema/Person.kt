package com.quangio.vertxsample.domain.schema

import com.fasterxml.jackson.annotation.JsonIgnore
import io.requery.*

@Entity
interface Person : Persistable {
  @get:Key
  @get:Generated
  var id: Int
  var name: String

  @get:Column(unique = true, index = true, nullable = false)
  @get:JsonIgnore
  var email: String

  @get:Column(nullable = true)
  var intro: String?

  @get:Column(nullable = true)
  var wantTeam: String?

  @get:Column
  var fbId: Long

  @get:JsonIgnore
  // @get:ForeignKey(references = Team::class, referencedColumn = "id")
  @get:ManyToOne
  @get:Column(name = "team")
  var team: Team
}
