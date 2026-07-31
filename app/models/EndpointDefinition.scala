package models

/** Static reference data describing one MTD API endpoint the dashboard recognises. */
final case class EndpointDefinition(
  apiArea: String,
  controller: String,
  endpointName: String,
  httpMethod: String,
  pathTemplate: String,
  displayName: String,
  regime: TaxRegime = TaxRegime.Itsa
) {
  val key: String = s"$controller.$endpointName"
}
