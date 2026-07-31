resolvers += MavenRepository("HMRC-open-artefacts-maven2", "https://open.artefacts.tax.service.gov.uk/maven2")
resolvers += Resolver.url("HMRC-open-artefacts-ivy2", url("https://open.artefacts.tax.service.gov.uk/ivy2"))(Resolver.ivyStylePatterns)

addSbtPlugin("org.playframework" % "sbt-plugin" % "3.0.9")

// uk.gov.hmrc sbt-auto-build / sbt-distributables are deliberately omitted: they drive
// MDTP build/release conventions that only apply on the platform's Jenkins/CI.
