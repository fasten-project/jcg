javacOptions in ThisBuild ++= Seq("-encoding", "utf8", "-parameters")

lazy val commonSettings = Seq(
    scalaVersion := "2.12.6",
    organization := "de.opal-project",
    homepage := Some(url("https://bitbucket.org/delors/jcg")),
    licenses := Seq("BSD-2-Clause" -> url("http://opensource.org/licenses/BSD-2-Clause")),
    resolvers += Resolver.sonatypeRepo("snapshots"),
    version := "1.0",
    publishMavenStyle := true,
    publishTo := MavenPublishing.publishTo(isSnapshot.value),
    pomExtra := MavenPublishing.pomNodeSeq()
)

lazy val jcg_annotations = project.settings(
    commonSettings,
    name := "JCG Annotations",
    aggregate in assembly := false
)

lazy val jcg_data_format = project.settings(
    commonSettings,
    name := "JCG Data Format",
    aggregate in assembly := false,
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.9",
    libraryDependencies += "io.get-coursier" %% "coursier" % "1.0.3",
    libraryDependencies += "io.get-coursier" %% "coursier-cache" % "1.0.3"
)

lazy val jcg_testcases = project.settings(
    commonSettings,
    name := "JCG Test Cases",
    libraryDependencies += "commons-io" % "commons-io" % "2.5",
    aggregate in assembly := false,
    compileOrder := CompileOrder.Mixed
).dependsOn(jcg_annotations, jcg_data_format)

lazy val jcg_annotation_matcher = project.settings(
    commonSettings,
    name := "JCG Annotation Matcher",
    libraryDependencies += "de.opal-project" %% "bytecode-representation" % "3.0.0-SNAPSHOT",
    aggregate in assembly := false
).dependsOn(jcg_annotations, jcg_testadapter_commons)

lazy val jcg_wala_testadapter = project.settings(
    commonSettings,
    name := "JCG WALA Test Adapter",
    resolvers += Resolver.sonatypeRepo("snapshots"),
    libraryDependencies += "com.ibm.wala" % "com.ibm.wala.core" % "1.5.2",
    libraryDependencies += "com.ibm.wala" % "com.ibm.wala.util" % "1.5.2",
    libraryDependencies += "com.ibm.wala" % "com.ibm.wala.shrike" % "1.5.2",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.9",
    aggregate in assembly := false,
    publishArtifact := false
).dependsOn(jcg_data_format, jcg_testadapter_commons)

lazy val jcg_soot_testadapter = project.settings(
    commonSettings,
    name := "JCG Soot Test Adapter",
    resolvers += "soot snapshot" at "https://soot-build.cs.uni-paderborn.de/nexus/repository/soot-snapshot/",
    resolvers += "soot release" at "https://soot-build.cs.uni-paderborn.de/nexus/repository/soot-release/",
    libraryDependencies += "ca.mcgill.sable" % "soot" % "3.1.0",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.9",
    aggregate in assembly := false,
    publishArtifact := false
).dependsOn(jcg_testadapter_commons)

lazy val jcg_opal_testadapter = project.settings(
    commonSettings,
    name := "JCG OPAL Test Adapter",
    libraryDependencies += "de.opal-project" %% "three-address-code" % "3.0.0-SNAPSHOT",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.9",
    aggregate in assembly := false,
    publishArtifact := false
).dependsOn(
    jcg_annotation_matcher, // TODO
    jcg_testadapter_commons
)


//lazy val jcg_opal_testadapter = project.settings(
//    commonSettings,
//    aggregate in assembly := false
//).dependsOn(jcg_testadapter_commons)

lazy val jcg_doop_testadapter = project.settings(
    commonSettings,
    name := "JCG DOOP Test Adapter",
    libraryDependencies += "de.opal-project" %% "bytecode-representation" % "3.0.0-SNAPSHOT",
    libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.9",
    aggregate in assembly := false,
    publishArtifact := false
).dependsOn(
    jcg_annotation_matcher, // TODO
    jcg_testadapter_commons
)

lazy val jcg_testadapter_commons = project.settings(
    commonSettings,
    name := "JCG Test Adapter Commons",
    aggregate in assembly := false
).dependsOn(jcg_data_format)

lazy val jcg_evaluation = project.settings(
    commonSettings,
    name := "JCG Evaluation",
    resolvers += "soot snapshot" at "https://soot-build.cs.uni-paderborn.de/nexus/repository/soot-snapshot/",
    resolvers += "soot release" at "https://soot-build.cs.uni-paderborn.de/nexus/repository/soot-release/",
    resolvers += Resolver.mavenLocal,
    libraryDependencies += "de.opal-project" %% "hermes" % "3.0.0-SNAPSHOT",
    publishArtifact := false
).dependsOn(
    jcg_testcases,
    jcg_data_format,
    jcg_annotation_matcher,
    //    jcg_opal_testadapter,
    jcg_wala_testadapter,
    jcg_soot_testadapter,
    jcg_opal_testadapter
)
