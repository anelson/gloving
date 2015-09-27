// You may use this file to add plugin dependencies for sbt.

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

// scalastyle: coding style check and enforcer
addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.0")
