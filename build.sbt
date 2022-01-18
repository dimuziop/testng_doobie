

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val DoobieVersion = "1.0.0-RC1"
val NewTypeVersion = "0.4.4"

lazy val doobiePostgresMigration = ProjectRef(uri("git://github.com/nrkno/doobie-postgres-migration.git"), "doobie-postgres-migration")

lazy val root = (project in file("."))
  .dependsOn(doobiePostgresMigration)
  .settings(
    name := "Doobie",
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core" % DoobieVersion,
      "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
      "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
      "io.estatico" %% "newtype" % NewTypeVersion
    )
  )




