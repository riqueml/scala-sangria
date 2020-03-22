package com.howtographql.scala.sangria

import scala.concurrent._
import scala.concurrent.duration.Duration

import com.howtographql.scala.sangria.models.{AuthenticationException, AuthorizationException, User}

case class MyContext(dao:DAO, currentUser: Option[User] = None){
    def login(email: String, password: String): User = {
        val userOpt = Await.result(dao.authenticate(email,password), Duration.Inf)
        userOpt.getOrElse(
            throw AuthenticationException("email or password are invalid")
        )
    }
    def ensureAuthenticated() = if(currentUser.isEmpty)
        throw AuthorizationException("you do not have permission. please sign in.")
}