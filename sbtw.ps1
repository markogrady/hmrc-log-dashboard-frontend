# sbt wrapper: pins JDK 21 (Java 26 on this machine is not validated for Scala 3.3 / sbt / Pekko).
# Usage: .\sbtw.ps1 run     |     .\sbtw.ps1 test     |     .\sbtw.ps1 "run -Dapplication.router=testOnlyDoNotUseInAppConf.Routes"
$jdk = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "jdk-21*" | Select-Object -First 1
if (-not $jdk) { Write-Error "Temurin 21 not found. Install with: winget install EclipseAdoptium.Temurin.21.JDK"; exit 1 }
$env:JAVA_HOME = $jdk.FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
sbt @args
