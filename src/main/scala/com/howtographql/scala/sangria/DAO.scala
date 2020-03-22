package com.howtographql.scala.sangria

import scala.language.postfixOps
import DBSchema._
import slick.jdbc.H2Profile.api._
import scala.concurrent.Future
import sangria.execution.deferred.{RelationIds, SimpleRelation}

import com.howtographql.scala.sangria.models._

class DAO(db: Database) {
    def allUsers = db.run(Users.result)
    def getUsers(ids:Seq[Int]) = db.run(
        Users.filter(_.id inSet ids).result
    )

    def allVotes = db.run(Votes.result)
    def getVotes(ids:Seq[Int]) = db.run(
        Votes.filter(_.id inSet ids).result
    )

    def allLinks = db.run(Links.result)
    def getLinks(ids:Seq[Int]) = db.run(
        Links.filter(_.id inSet ids).result
    )

    def getLinksByUserIds(ids: Seq[Int]):Future[Seq[Link]] = { 
        db.run {
            Links.filter(_.postedBy inSet ids).result
        }
    }

    def getVotesByRelationIds(rel: RelationIds[Vote]):Future[Seq[Vote]] = {
        db.run {
            Votes.filter { vote => rel.rawIds.collect({
                case (SimpleRelation("byUser"), ids: Seq[Int]) => vote.userId inSet ids
                case (SimpleRelation("byLink"), ids: Seq[Int]) => vote.linkId inSet ids
            }).foldLeft(true: Rep[Boolean])(_ || _)
            } result
        }
    }

    def createUser(name: String, authProvider: AuthProviderSignupData): Future[User] = {
        val newUser = User(0, name, authProvider.auth.email, authProvider.auth.password)
        val insertAndReturnUserQuery = (Users returning Users.map(_.id)) into {
            (user, id) => user.copy(id = id)
        }
        db.run {
            insertAndReturnUserQuery += newUser
        }
    }

    def createLink(url: String, description: String, postedBy: Int): Future[Link] = {
        val insertAndReturnLinkQuery = (Links returning Links.map(_.id)) into {
            (link, id) => link.copy(id = id)
        }
        db.run {
            insertAndReturnLinkQuery += Link(0, url, description, postedBy)
        }
    }

    def createVote(userId: Int, linkId: Int): Future[Vote] = {
        val insertAndReturnVoteQuery = (Votes returning Votes.map(_.id)) into {
            (vote, id) => vote.copy(id = id)
        }
        db.run {
            insertAndReturnVoteQuery += Vote(0, userId, linkId)
        }
    }

    def authenticate(email: String, password: String): Future[Option[User]] = db.run {
        Users.filter(u => u.email === email && u.password === password).result.headOption
    }
}
