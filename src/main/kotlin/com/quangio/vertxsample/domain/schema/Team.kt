package com.quangio.vertxsample.domain.schema

import io.requery.*

@Entity
interface Team : Persistable {
  @get:Key
  @get:Generated
  var id: Int

  var size: Int

  @get:OneToMany(mappedBy = "team")
  var members: List<Person>
}
