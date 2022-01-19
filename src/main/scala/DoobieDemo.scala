import cats.effect.{ExitCode, IO, IOApp}
import doobie._
import cats.implicits._
import doobie.implicits._
import doobie.migration.DoobiePostgresMigration
import doobie.util.transactor.Transactor
import doobie.util.update.Update

import java.io.File
import java.util.UUID

object DoobieDemo extends IOApp {

  case class Actor(id: Int, name: String)

  case class Movie(id: String, title: String, year: Int, actors: List[String], director: String)


  implicit class Debugger[A](io: IO[A]) {
    def debug: IO[A] = io.map { a =>
      println(s"[${Thread.currentThread().getName}] - $a")
      a
    }
  }

  val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:myimdb",
    "docker", // username
    "docker"
  )

  def findAllActorNames: IO[List[String]] = {
    val query = sql"select name from actors".query[String]
    val action = query.to[List]
    action.transact(xa)
  }

  def findActorById(id: Int): IO[Option[Actor]] = {
    val query = sql"select id, name from actors where id=$id".query[Actor]
    val action = query.option
    action.transact(xa)
  }

  val actorsNamesStream = sql"select name from actors".query[String]
    .stream.compile.toList.transact(xa)

  // HC, HPS
  def findActorByName(name: String): IO[Option[Actor]] = {
    val queryStr = "select id, name from actors where name = ?"
    HC.stream[Actor](queryStr, HPS.set(name), 100).compile.toList.map(_.headOption).transact(xa)
  }

  // fragments
  def findActorsWyInitial(letter: String): IO[List[Actor]] = {
    val selectPart = fr"select id, name"
    val fromPart = fr"from actors"
    val wherePart = fr"where LEFT(name,1) = $letter"

    val statement = selectPart ++ fromPart ++ wherePart

    statement.query[Actor].stream.compile.toList.transact(xa)

  }

  // update
  def saveActor(id: Int, name: String): IO[Int] = {
    val query = sql"insert into actors (id, name) values ($id, $name)"
    query.update.run.transact(xa)
  }

  def saveActor_v2(id: Int, name: String): IO[Int] = {
    val query = "insert into actors (id, name) values (?, ?)"
    Update[Actor](query).run(Actor(id, name)).transact(xa)
  }

  // update with autogenerated IDs
  def saveActorAutoGenerated(name: String): IO[Int] = {
    sql"insert into actors (name) values ($name)".update.withUniqueGeneratedKeys[Int]("id").transact(xa)
  }

  // update insert many
  def saveMultipleActorsBy(actorNames: List[String]): IO[List[Actor]] = {
    val insertStatement = "insert into actors (name) values (?)"
    val updateAction = Update[String](insertStatement).updateManyWithGeneratedKeys[Actor]("id", "name")(actorNames)
    updateAction.compile.toList.transact(xa)
  }

  // type classes
  class ActorName(val value: String) {
    override def toString: String = value

    override def equals(obj: Any): Boolean = obj match {
      case name: ActorName => value == name.value
      case _ => false
    }

    override def hashCode(): Int = value.hashCode

  }

  object ActorName {
    implicit val actorNameGet: Get[ActorName] = Get[String].map(str => new ActorName(str))
    implicit val actorNamePut: Put[ActorName] = Put[String].contramap(actorName => actorName.value)
  }

  // "value type"
  case class DirectorId(id: Int)

  case class DirectorName(name: String)

  case class DirectorLastName(lastName: String)

  case class Director(id: DirectorId, name: DirectorName, lastName: DirectorLastName)

  object Director {
    implicit val directorRead: Read[Director] = Read[(Int, String, String)].map {
      case (id, name, lastName) => Director(DirectorId(id), DirectorName(name), DirectorLastName(lastName))
    }

    implicit val directorWrite: Write[Director] = Write[(Int, String, String)]
      .contramap {
        case Director(DirectorId(id), DirectorName(name), DirectorLastName(lastName)) => (id, name, lastName)
      }
  }

  def findAllActorNamesCustomClass: IO[List[ActorName]] =
    sql"select name from actors".query[ActorName].to[List].transact(xa)


  import doobie.postgres._
  import doobie.postgres.implicits._


  // write large queries
  def findMovieByTitle(title: String): IO[Option[Movie]] = {
    val statement =
      sql"""
        SELECT m.id, m.title, m.year_of_production, array_agg(a.name) as actors, d.name || ' ' || d.last_name
        FROM movies m
            JOIN movies_actors ma ON m.id = ma.movie_id
            JOIN actors a ON ma.actor_id = a.id
            JOIN directors d ON m.director_id = d.id
        WHERE m.title = $title
        GROUP BY (m.id, m.title, m.year_of_production, d.name, d.last_name)
        """
    statement.query[Movie].option.transact(xa)
  }

  def findMovieByTitle_v2(title: String): IO[Option[Movie]] = {
    def findMovieByTitle() =
      sql"SELECT id, title, year_of_production, director_id FROM movies WHERE title = $title"
        .query[(UUID, String, Int, Int)].option

    def findDirectorById(directorId: Int) =
      sql"SELECT name, last_name FROM directors WHERE id = $directorId"
        .query[(String, String)].option

    def findActorsByMovieId(movieId: UUID) =
      sql"SELECT a.name FROM actors a JOIN movies_actors ma ON a.id = ma.actor_id WHERE ma.movie_id = $movieId"
        .query[String].to[List]

    val query = for {
      maybeMovie <- findMovieByTitle()
      maybeDirector <- maybeMovie match {
        case Some((_, _, _, directorId)) => findDirectorById(directorId)
        case None => Option.empty[(String, String)].pure[ConnectionIO]
      }
      actors <- maybeMovie match {
        case Some((movieId, _, _, _)) => findActorsByMovieId(movieId)
        case None => List.empty[String].pure[ConnectionIO]
      }
    } yield for {
      (id, t, year, _) <- maybeMovie
      (firstName, lastName) <- maybeDirector
    } yield Movie(id.toString, t, year, actors, s"$firstName $lastName")

    query.transact(xa)
  }

  override def run(args: List[String]): IO[ExitCode] = {
    //DoobiePostgresMigration.executeMigrationsIO(new File("src/main/resources/migrations"), xa).as(ExitCode.Success)
    //saveMultipleActorsBy(List("Carl", "Tito", "Largo", "Cabezon", "Willy")).debug.as(ExitCode.Success)
    //findMovieByTitle("Zack Snyder's Justice League").debug.as(ExitCode.Success)
    findMovieByTitle_v2("Zack Snyder's Justice League").debug.as(ExitCode.Success)
    //findAllActorNamesCustomClass.flatMap(list => IO(list.distinct)).debug.as(ExitCode.Success)
  }

}
