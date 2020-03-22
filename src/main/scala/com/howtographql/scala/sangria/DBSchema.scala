package com.howtographql.scala.sangria

import java.sql.Timestamp

import slick.jdbc.H2Profile.api._
import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps
import akka.http.scaladsl.model.DateTime

import com.howtographql.scala.sangria.models._

object DBSchema {
  implicit val dateTimeColumnType = MappedColumnType.base[DateTime, Timestamp](
    dt => new Timestamp(dt.clicks),
    ts => DateTime(ts.getTime)
  )
  class UsersTable(tag:Tag) extends Table[User](tag, "USERS"){
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def email = column[String]("EMAIL")
    def password = column[String]("PASS")
    def createdAt = column[DateTime]("CREATED_AT")

    def * = (id,name,email,password,createdAt).mapTo[User]
  }

  class LinksTable(tag: Tag) extends Table[Link](tag, "LINKS"){
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def url = column[String]("URL")
    def description = column[String]("DESCRIPTION")
    def postedBy = column[Int]("POSTED_BY")
    def createdAt = column[DateTime]("CREATED_AT")

    def * = (id, url, description, postedBy, createdAt).mapTo[Link]

    def postedByFK = foreignKey("postedBy_FK", postedBy, Users)(_.id)
  }

  class VotesTable(tag: Tag) extends Table[Vote](tag, "VOTES"){
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def userId = column[Int]("USER_ID")
    def linkId = column[Int]("LINK_ID")
    def createdAt = column[DateTime]("CREATED_AT")

    def * = (id, userId, linkId, createdAt).mapTo[Vote]

    def userFK = foreignKey("user_FK", userId, Users)(_.id)
    def linkFK = foreignKey("link_FK", linkId, Links)(_.id)
  }

  val Users = TableQuery[UsersTable]
  val Votes = TableQuery[VotesTable]
  val Links = TableQuery[LinksTable]

  val databaseSetup = DBIO.seq(
    Users.schema.create,
    Users forceInsertAll Seq(
      User(1, "Rique", "riqueml@example.com", "cubo123"),
      User(2, "Wagner", "wagner@example.com", "cubo123")
    ),

    Links.schema.create,
    Links forceInsertAll Seq(
      Link(1, "http://howtographql.com", "Awesome community driven GraphQL tutorial", 1, DateTime(2017,9,12)),
      Link(2, "http://graphql.org", "Official GraphQL web page", 2, DateTime(2018,10,1)),
      Link(3, "https://facebook.github.io/graphql/", "GraphQL specification", 1, DateTime(2019,1,5))
    ),

    Votes.schema.create,
    Votes forceInsertAll Seq(
      Vote(1,1,1),
      Vote(2,2,1),
      Vote(3,1,3)
    )
  )

  def createDatabase: DAO = {
    val db = Database.forConfig("h2mem")

    Await.result(db.run(databaseSetup), 10 seconds)

    new DAO(db)
  }
}
