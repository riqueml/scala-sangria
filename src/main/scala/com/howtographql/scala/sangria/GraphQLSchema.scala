package com.howtographql.scala.sangria

import akka.http.scaladsl.model.DateTime
import sangria.schema.{Field, ListType, ObjectType, InputObjectType}

import models._
import sangria.schema._
import sangria.macros.derive._
import sangria.execution.deferred.{Fetcher, DeferredResolver, Relation, RelationIds}
import sangria.ast.StringValue

object GraphQLSchema {
    val IdentifiableType = InterfaceType(
        "Identifiable", fields[Unit, Identifiable](
            Field("id", IntType, resolve = _.value.id)
        )
    )
    implicit val GraphQLDateTime = ScalarType[DateTime](
        "DateTime",
        coerceOutput = (dt,_) => dt.toString,
        coerceInput = {
            case StringValue(dt,_,_) => DateTime.fromIsoDateTimeString(dt).toRight(DateTimeCoerceViolation)
            case _ => Left(DateTimeCoerceViolation)
        },
        coerceUserInput = {
            case s: String => DateTime.fromIsoDateTimeString(s).toRight(DateTimeCoerceViolation)
            case _ => Left(DateTimeCoerceViolation)
        }
    )
    implicit val AuthProviderEmailInputType: InputObjectType[AuthProviderEmail] = deriveInputObjectType[AuthProviderEmail] (
        InputObjectTypeName("AUTH_PROVIDER_EMAIL")
    )
    lazy val AuthProviderSignupDataInputType: InputObjectType[AuthProviderSignupData] = deriveInputObjectType[AuthProviderSignupData] (

    )
    import sangria.marshalling.sprayJson._
    import spray.json.DefaultJsonProtocol._

    implicit val authProviderEmailFormat = jsonFormat2(AuthProviderEmail)
    implicit val authProviderSignupDataFormat = jsonFormat1(AuthProviderSignupData)

    lazy val UserType:ObjectType[Unit, User] = deriveObjectType[Unit, User](
        Interfaces(IdentifiableType),
        AddFields(
            Field("links", ListType(LinkType),
                resolve = c => linksFetcher.deferRelSeq(linkByUserRel, c.value.id)),
            Field("votes", ListType(VoteType),
                resolve = c => votesFetcher.deferRelSeq(voteByUserRel, c.value.id))
        )
    )
    lazy val LinkType:ObjectType[Unit, Link] = deriveObjectType[Unit, Link](
        Interfaces(IdentifiableType),
        ReplaceField("postedBy",
            Field("postedBy", UserType, resolve = c => usersFetcher.defer(c.value.postedBy))
        ),
        AddFields(
            Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(voteByLinkRel, c.value.id))
        )
    )
    lazy val VoteType:ObjectType[Unit, Vote] = deriveObjectType[Unit, Vote](
        Interfaces(IdentifiableType),
        ExcludeFields("userId"),
        AddFields(
            Field("user", UserType, resolve = c => usersFetcher.defer(c.value.userId)),
            Field("link", LinkType, resolve = c => linksFetcher.defer(c.value.linkId))
        )
    )

    val linkByUserRel = Relation[Link, Int]("byUser", l => Seq(l.postedBy))
    val voteByUserRel = Relation[Vote, Int]("byUser", v => Seq(v.userId))
    val voteByLinkRel = Relation[Vote, Int]("byLink", v => Seq(v.linkId))

    val usersFetcher = Fetcher(
        (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getUsers(ids),
    )
    val linksFetcher = Fetcher.rel(
        (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getLinks(ids),
        (ctx: MyContext, ids: RelationIds[Link]) => ctx.dao.getLinksByUserIds(ids(linkByUserRel))
    )
    val votesFetcher = Fetcher.rel(
        (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getVotes(ids),
        (ctx: MyContext, ids: RelationIds[Vote]) => ctx.dao.getVotesByRelationIds(ids)
    )

    val Resolver = DeferredResolver.fetchers(usersFetcher, votesFetcher, linksFetcher)

    val Id = Argument("id",IntType)
    val Ids = Argument("ids",ListInputType(IntType))
    val QueryType = ObjectType(
        "Query",
        fields[MyContext, Unit](
            Field("allUsers", ListType(UserType),
                resolve = c => c.ctx.dao.allUsers),
            Field("user", OptionType(UserType),
                arguments = Id :: Nil,
                resolve = c => usersFetcher.deferOpt(c.arg(Id))),
            Field("users", ListType(UserType),
                arguments = Ids :: Nil,
                resolve = c => usersFetcher.deferSeq(c.arg(Ids))),

            Field("allVotes", ListType(VoteType),
                resolve = c => c.ctx.dao.allVotes),
            Field("vote", OptionType(VoteType),
                arguments = Id :: Nil,
                resolve = c => votesFetcher.deferOpt(c.arg(Id))),
            Field("votes", ListType(VoteType),
                arguments = Ids :: Nil,
                resolve = c => votesFetcher.deferSeq(c.arg(Ids))),

            Field("allLinks", ListType(LinkType), 
                resolve = c => c.ctx.dao.allLinks),
            Field("link", OptionType(LinkType), 
                arguments = Id :: Nil,
                resolve = c => linksFetcher.deferOpt(c.arg(Id))),
            Field("links", ListType(LinkType),
                arguments = Ids :: Nil,
                resolve = c => linksFetcher.deferSeq(c.arg(Ids)))
        )
    )

    val NameArg = Argument("name",StringType)
    val AuthProviderArg = Argument("authProvider", AuthProviderSignupDataInputType)

    val UrlArg = Argument("url",StringType)
    val DescArg = Argument("description", StringType)
    //val PostedByArg = Argument("postedById", IntType)

    val UserIdArg = Argument("userId",IntType)
    val LinkIdArg = Argument("linkId",IntType)

    val EmailArg = Argument("email",StringType)
    val PasswordArg = Argument("password",StringType)

    val MutationType = ObjectType(
        "Mutation", fields[MyContext, Unit] (
            Field("createUser", UserType,
                arguments = NameArg :: AuthProviderArg :: Nil,
                resolve = c => c.ctx.dao.createUser(c.arg(NameArg), c.arg(AuthProviderArg))),
            Field("createLink", LinkType,
                arguments = UrlArg :: DescArg :: Nil,
                tags = Authorized :: Nil,
                resolve = c => c.ctx.dao.createLink(c.arg(UrlArg), c.arg(DescArg), c.ctx.currentUser.get.id)),
            Field("createVote", VoteType,
                arguments = LinkIdArg :: Nil,
                tags = Authorized :: Nil,
                resolve = c => c.ctx.dao.createVote(c.ctx.currentUser.get.id, c.arg(LinkIdArg))),
            Field("login", UserType,
                arguments = EmailArg :: PasswordArg :: Nil,
                resolve = ctx => UpdateCtx(
                    ctx.ctx.login(ctx.arg(EmailArg), ctx.arg(PasswordArg))) {user =>
                    ctx.ctx.copy(currentUser = Some(user))})
        )

    )

    val SchemaDefinition = Schema(QueryType, Some(MutationType))
}