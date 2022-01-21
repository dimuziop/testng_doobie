package ddl

import doobie._
import doobie.implicits._
import doobie.util.ExecutionContexts
import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import ddl.Migrations.usersTable

/**
 * User: pat
 * Date: 21/1/22
 * Time: 9:03
 */
object Migrator  extends IOApp {

  val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:myimdb",
    "docker", // username
    "docker"
  )

  val migrationsTable: doobie.ConnectionIO[Int] =
    sql"""
      CREATE TABLE IF NOT EXISTS migrations (
        id         uuid              NOT NULL,
        name  character varying NOT NULL,
        descriptor       character varying NOT NULL,
        hash  character varying NOT NULL,
        created_at timestamp         NOT NULL,
        updated_at timestamp         NULL default NULL,
        deleted_at timestamp         NULL default NULL
      )
    """.update.run

  val migrations: List[doobie.ConnectionIO[Int]] = List(
    migrationsTable
  )

  implicit class Debugger[A](io: IO[A]) {
    def debug: IO[A] = io.map { a =>
      println(s"[${Thread.currentThread().getName}] - $a")
      a
    }
  }

  val y = xa.yolo
  import y._

  override def run(args: List[String]): IO[ExitCode] =
    (migrationsTable, usersTable).mapN(_ + _).transact(xa).unsafeRunSync().debug.as(ExitCode.Success)
}
