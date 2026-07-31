package models.viewmodels

final case class DashboardPageViewModel(
  applications: Seq[ApplicationOption],
  selectedApp: ApplicationOption,
  months: Seq[MonthOption],
  selectedMonth: MonthOption,
  isCurrentMonth: Boolean,
  clients: Seq[String],
  selectedClient: Option[String],
  summary: DashboardSummary
)
