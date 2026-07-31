package models

import java.util.regex.Pattern

/**
 * Static catalog of the MTD API endpoints the dashboard recognises, keyed by the
 * [Controller][endpointName] pair used in HMRC log messages (hmrc/vat-api convention),
 * with a route-template fallback matcher for lines that only carry a path.
 */
object ItsaEndpointCatalog {

  import TaxRegime.Vat

  val all: List[EndpointDefinition] = List(
    // Individual Calculations
    EndpointDefinition("Individual Calculations", "TriggerCalculationController", "triggerCalculation", "POST", "individuals/calculations/{nino}/self-assessment/{taxYear}/trigger/{calculationType}", "Trigger a self assessment tax calculation"),
    EndpointDefinition("Individual Calculations", "ListCalculationsController", "listCalculations", "GET", "individuals/calculations/{nino}/self-assessment/{taxYear}", "List self assessment tax calculations"),
    EndpointDefinition("Individual Calculations", "RetrieveCalculationController", "retrieveCalculation", "GET", "individuals/calculations/{nino}/self-assessment/{taxYear}/{calculationId}", "Retrieve a self assessment tax calculation"),
    EndpointDefinition("Individual Calculations", "SubmitFinalDeclarationController", "submitFinalDeclaration", "POST", "individuals/calculations/{nino}/self-assessment/{taxYear}/{calculationId}/{calculationType}", "Submit a final declaration"),

    // Obligations
    EndpointDefinition("Obligations", "RetrieveItsaObligationsController", "retrieveItsaObligations", "GET", "obligations/details/{nino}/income-and-expenditure", "Retrieve income and expenditure obligations"),
    EndpointDefinition("Obligations", "RetrieveCrystallisationObligationsController", "retrieveCrystallisationObligations", "GET", "obligations/details/{nino}/crystallisation", "Retrieve final declaration obligations"),

    // Business Details
    EndpointDefinition("Business Details", "ListBusinessesController", "listBusinesses", "GET", "individuals/business/details/{nino}/list", "List all businesses"),
    EndpointDefinition("Business Details", "RetrieveBusinessDetailsController", "retrieveBusinessDetails", "GET", "individuals/business/details/{nino}/{businessId}", "Retrieve business details"),
    EndpointDefinition("Business Details", "UpdateAccountingTypeController", "updateAccountingType", "PUT", "individuals/business/details/{nino}/{businessId}/{taxYear}/accounting-type", "Update accounting type"),
    EndpointDefinition("Business Details", "CreateUpdatePeriodsOfAccountController", "createUpdatePeriodsOfAccount", "PUT", "individuals/business/details/{nino}/{businessId}/{taxYear}/periods-of-account", "Create or update periods of account"),

    // Self-Employment Business
    EndpointDefinition("Self-Employment Business", "RetrieveSelfEmploymentAnnualSubmissionController", "retrieveSelfEmploymentAnnualSubmission", "GET", "individuals/business/self-employment/{nino}/{businessId}/annual/{taxYear}", "Retrieve a self-employment annual submission"),
    EndpointDefinition("Self-Employment Business", "AmendSelfEmploymentAnnualSubmissionController", "amendSelfEmploymentAnnualSubmission", "PUT", "individuals/business/self-employment/{nino}/{businessId}/annual/{taxYear}", "Create or amend a self-employment annual submission"),
    EndpointDefinition("Self-Employment Business", "DeleteSelfEmploymentAnnualSubmissionController", "deleteSelfEmploymentAnnualSubmission", "DELETE", "individuals/business/self-employment/{nino}/{businessId}/annual/{taxYear}", "Delete a self-employment annual submission"),
    EndpointDefinition("Self-Employment Business", "CreateSelfEmploymentPeriodSummaryController", "createSelfEmploymentPeriodSummary", "POST", "individuals/business/self-employment/{nino}/{businessId}/period", "Create a self-employment period summary"),
    EndpointDefinition("Self-Employment Business", "ListSelfEmploymentPeriodSummariesController", "listSelfEmploymentPeriodSummaries", "GET", "individuals/business/self-employment/{nino}/{businessId}/period/{taxYear}", "List self-employment period summaries"),
    EndpointDefinition("Self-Employment Business", "RetrieveSelfEmploymentCumulativeSummaryController", "retrieveSelfEmploymentCumulativeSummary", "GET", "individuals/business/self-employment/{nino}/{businessId}/cumulative/{taxYear}", "Retrieve a self-employment cumulative period summary"),
    EndpointDefinition("Self-Employment Business", "AmendSelfEmploymentCumulativeSummaryController", "amendSelfEmploymentCumulativeSummary", "PUT", "individuals/business/self-employment/{nino}/{businessId}/cumulative/{taxYear}", "Create or amend a self-employment cumulative period summary"),

    // UK Property Business
    EndpointDefinition("UK Property Business", "RetrieveUkPropertyAnnualSubmissionController", "retrieveUkPropertyAnnualSubmission", "GET", "individuals/business/property/uk/{nino}/{businessId}/annual/{taxYear}", "Retrieve a UK property annual submission"),
    EndpointDefinition("UK Property Business", "AmendUkPropertyAnnualSubmissionController", "amendUkPropertyAnnualSubmission", "PUT", "individuals/business/property/uk/{nino}/{businessId}/annual/{taxYear}", "Create or amend a UK property annual submission"),
    EndpointDefinition("UK Property Business", "RetrieveUkPropertyCumulativeSummaryController", "retrieveUkPropertyCumulativeSummary", "GET", "individuals/business/property/uk/{nino}/{businessId}/cumulative/{taxYear}", "Retrieve a UK property cumulative period summary"),
    EndpointDefinition("UK Property Business", "AmendUkPropertyCumulativeSummaryController", "amendUkPropertyCumulativeSummary", "PUT", "individuals/business/property/uk/{nino}/{businessId}/cumulative/{taxYear}", "Create or amend a UK property cumulative period summary"),

    // Foreign Property Business
    EndpointDefinition("Foreign Property Business", "RetrieveForeignPropertyCumulativeSummaryController", "retrieveForeignPropertyCumulativeSummary", "GET", "individuals/business/property/foreign/{nino}/{businessId}/cumulative/{taxYear}", "Retrieve a foreign property cumulative period summary"),
    EndpointDefinition("Foreign Property Business", "AmendForeignPropertyCumulativeSummaryController", "amendForeignPropertyCumulativeSummary", "PUT", "individuals/business/property/foreign/{nino}/{businessId}/cumulative/{taxYear}", "Create or amend a foreign property cumulative period summary"),

    // Individual Losses
    EndpointDefinition("Individual Losses", "ListBFLossesController", "listBroughtForwardLosses", "GET", "individuals/losses/{nino}/brought-forward-losses/tax-year/{taxYear}", "List brought forward losses"),
    EndpointDefinition("Individual Losses", "CreateBFLossController", "createBroughtForwardLoss", "POST", "individuals/losses/{nino}/brought-forward-losses/tax-year/brought-forward-from/{taxYear}", "Create a brought forward loss"),
    EndpointDefinition("Individual Losses", "RetrieveBFLossController", "retrieveBroughtForwardLoss", "GET", "individuals/losses/{nino}/brought-forward-losses/{lossId}", "Retrieve a brought forward loss"),
    EndpointDefinition("Individual Losses", "AmendBFLossController", "amendBroughtForwardLossAmount", "POST", "individuals/losses/{nino}/brought-forward-losses/{lossId}/tax-year/{taxYear}/change-loss-amount", "Amend a brought forward loss amount"),
    EndpointDefinition("Individual Losses", "DeleteBFLossController", "deleteBroughtForwardLoss", "DELETE", "individuals/losses/{nino}/brought-forward-losses/{lossId}/tax-year/{taxYear}", "Delete a brought forward loss"),
    EndpointDefinition("Individual Losses", "ListLossClaimsController", "listLossClaims", "GET", "individuals/losses/{nino}/loss-claims/tax-year/{taxYear}", "List loss claims"),
    EndpointDefinition("Individual Losses", "CreateLossClaimController", "createLossClaim", "POST", "individuals/losses/{nino}/loss-claims", "Create a loss claim"),

    // CIS Deductions
    EndpointDefinition("CIS Deductions", "RetrieveCisDeductionsController", "retrieveCisDeductions", "GET", "individuals/deductions/cis/{nino}/current-position/{taxYear}/{source}", "Retrieve CIS deductions"),
    EndpointDefinition("CIS Deductions", "CreateCisDeductionsController", "createCisDeductions", "POST", "individuals/deductions/cis/{nino}/amendments", "Create CIS deductions"),
    EndpointDefinition("CIS Deductions", "AmendCisDeductionsController", "amendCisDeductions", "PUT", "individuals/deductions/cis/{nino}/amendments/{submissionId}", "Amend CIS deductions"),
    EndpointDefinition("CIS Deductions", "DeleteCisDeductionsController", "deleteCisDeductions", "DELETE", "individuals/deductions/cis/{nino}/amendments/{submissionId}/{taxYear}", "Delete CIS deductions"),

    // Employments Income
    EndpointDefinition("Employments Income", "ListEmploymentsController", "listEmployments", "GET", "individuals/employments-income/{nino}/{taxYear}", "List employments"),
    EndpointDefinition("Employments Income", "AddCustomEmploymentController", "addCustomEmployment", "POST", "individuals/employments-income/{nino}/{taxYear}", "Add a custom employment"),
    EndpointDefinition("Employments Income", "RetrieveEmploymentController", "retrieveEmployment", "GET", "individuals/employments-income/{nino}/{taxYear}/{employmentId}", "Retrieve an employment"),
    EndpointDefinition("Employments Income", "RetrieveFinancialDetailsController", "retrieveFinancialDetails", "GET", "individuals/employments-income/{nino}/{taxYear}/{employmentId}/financial-details", "Retrieve employment financial details"),
    EndpointDefinition("Employments Income", "AmendFinancialDetailsController", "amendFinancialDetails", "PUT", "individuals/employments-income/{nino}/{taxYear}/{employmentId}/financial-details", "Create or amend employment financial details"),
    EndpointDefinition("Employments Income", "IgnoreEmploymentController", "ignoreEmployment", "POST", "individuals/employments-income/{nino}/{taxYear}/{employmentId}/ignore", "Ignore an employment"),

    // Dividends Income
    EndpointDefinition("Dividends Income", "RetrieveDividendsController", "retrieveDividends", "GET", "individuals/dividends-income/{nino}/{taxYear}", "Retrieve dividends income"),
    EndpointDefinition("Dividends Income", "AmendDividendsController", "amendDividends", "PUT", "individuals/dividends-income/{nino}/{taxYear}", "Create or amend dividends income"),
    EndpointDefinition("Dividends Income", "RetrieveUkDividendsController", "retrieveUkDividends", "GET", "individuals/dividends-income/uk/{nino}/{taxYear}", "Retrieve UK dividends income"),
    EndpointDefinition("Dividends Income", "AmendUkDividendsController", "amendUkDividends", "PUT", "individuals/dividends-income/uk/{nino}/{taxYear}", "Create or amend UK dividends income"),

    // Savings Income
    EndpointDefinition("Savings Income", "AddUkSavingsAccountController", "addUkSavingsAccount", "POST", "individuals/savings-income/uk-accounts/{nino}", "Add a UK savings account"),
    EndpointDefinition("Savings Income", "ListUkSavingsAccountsController", "listUkSavingsAccounts", "GET", "individuals/savings-income/uk-accounts/{nino}", "List UK savings accounts"),
    EndpointDefinition("Savings Income", "RetrieveUkSavingsAccountSummaryController", "retrieveUkSavingsAccountSummary", "GET", "individuals/savings-income/uk-accounts/{nino}/{taxYear}/{savingsAccountId}", "Retrieve UK savings account annual summary"),
    EndpointDefinition("Savings Income", "AmendUkSavingsAccountSummaryController", "amendUkSavingsAccountSummary", "PUT", "individuals/savings-income/uk-accounts/{nino}/{taxYear}/{savingsAccountId}", "Create or amend UK savings account annual summary"),
    EndpointDefinition("Savings Income", "RetrieveOtherSavingsController", "retrieveOtherSavings", "GET", "individuals/savings-income/other/{nino}/{taxYear}", "Retrieve other savings income"),
    EndpointDefinition("Savings Income", "AmendOtherSavingsController", "amendOtherSavings", "PUT", "individuals/savings-income/other/{nino}/{taxYear}", "Create or amend other savings income"),

    // Pensions Income
    EndpointDefinition("Pensions Income", "RetrievePensionsIncomeController", "retrievePensionsIncome", "GET", "individuals/pensions-income/{nino}/{taxYear}", "Retrieve pensions income"),
    EndpointDefinition("Pensions Income", "AmendPensionsIncomeController", "amendPensionsIncome", "PUT", "individuals/pensions-income/{nino}/{taxYear}", "Create or amend pensions income"),
    EndpointDefinition("Pensions Income", "DeletePensionsIncomeController", "deletePensionsIncome", "DELETE", "individuals/pensions-income/{nino}/{taxYear}", "Delete pensions income"),

    // Individual Reliefs
    EndpointDefinition("Individual Reliefs", "RetrievePensionsReliefsController", "retrievePensionsReliefs", "GET", "individuals/reliefs/pensions/{nino}/{taxYear}", "Retrieve pensions reliefs"),
    EndpointDefinition("Individual Reliefs", "AmendPensionsReliefsController", "amendPensionsReliefs", "PUT", "individuals/reliefs/pensions/{nino}/{taxYear}", "Create or amend pensions reliefs"),
    EndpointDefinition("Individual Reliefs", "RetrieveCharitableGivingReliefController", "retrieveCharitableGivingRelief", "GET", "individuals/reliefs/charitable-giving/{nino}/{taxYear}", "Retrieve charitable giving relief"),
    EndpointDefinition("Individual Reliefs", "AmendCharitableGivingReliefController", "amendCharitableGivingRelief", "PUT", "individuals/reliefs/charitable-giving/{nino}/{taxYear}", "Create or amend charitable giving relief"),
    EndpointDefinition("Individual Reliefs", "RetrieveInvestmentReliefController", "retrieveInvestmentRelief", "GET", "individuals/reliefs/investment/{nino}/{taxYear}", "Retrieve investment relief"),

    // State Benefits
    EndpointDefinition("State Benefits", "CreateStateBenefitController", "createStateBenefit", "POST", "individuals/state-benefits/{nino}/{taxYear}", "Create a state benefit"),
    EndpointDefinition("State Benefits", "AmendStateBenefitAmountsController", "amendStateBenefitAmounts", "PUT", "individuals/state-benefits/{nino}/{taxYear}/{benefitId}/amounts", "Create or amend state benefit amounts"),
    EndpointDefinition("State Benefits", "DeleteStateBenefitController", "deleteStateBenefit", "DELETE", "individuals/state-benefits/{nino}/{taxYear}/{benefitId}", "Delete a state benefit"),

    // Individual Charges
    EndpointDefinition("Individual Charges", "RetrievePensionChargesController", "retrievePensionCharges", "GET", "individuals/charges/pensions/{nino}/{taxYear}", "Retrieve pension charges"),
    EndpointDefinition("Individual Charges", "AmendPensionChargesController", "amendPensionCharges", "PUT", "individuals/charges/pensions/{nino}/{taxYear}", "Create or amend pension charges"),
    EndpointDefinition("Individual Charges", "RetrieveHighIncomeChildBenefitController", "retrieveHighIncomeChildBenefit", "GET", "individuals/charges/high-income-child-benefit/{nino}/{taxYear}", "Retrieve HICBC submission"),

    // Capital Gains
    EndpointDefinition("Capital Gains", "RetrieveCgtResidentialPropertyController", "retrieveCgtResidentialProperty", "GET", "individuals/disposals-income/residential-property/{nino}/{taxYear}", "Retrieve CGT residential property disposals"),
    EndpointDefinition("Capital Gains", "CreateAmendCgtResidentialPropertyController", "createAmendCgtResidentialProperty", "PUT", "individuals/disposals-income/residential-property/{nino}/{taxYear}", "Create or amend CGT residential property disposals"),
    EndpointDefinition("Capital Gains", "RetrieveOtherCgtController", "retrieveOtherCgt", "GET", "individuals/disposals-income/other-gains/{nino}/{taxYear}", "Retrieve other capital gains"),

    // Business Source Adjustable Summary
    EndpointDefinition("Business Source Adjustable Summary", "ListBsasController", "listBsas", "GET", "individuals/self-assessment/adjustable-summary/{nino}/{taxYear}", "List BSAS"),
    EndpointDefinition("Business Source Adjustable Summary", "TriggerBsasController", "triggerBsas", "POST", "individuals/self-assessment/adjustable-summary/{nino}/trigger", "Trigger a BSAS"),
    EndpointDefinition("Business Source Adjustable Summary", "RetrieveSelfEmploymentBsasController", "retrieveSelfEmploymentBsas", "GET", "individuals/self-assessment/adjustable-summary/{nino}/self-employment/{calculationId}/{taxYear}", "Retrieve a self-employment BSAS"),
    EndpointDefinition("Business Source Adjustable Summary", "SubmitSelfEmploymentBsasController", "submitSelfEmploymentBsas", "POST", "individuals/self-assessment/adjustable-summary/{nino}/self-employment/{calculationId}/adjust/{taxYear}", "Submit self-employment BSAS adjustments"),
    EndpointDefinition("Business Source Adjustable Summary", "RetrieveUkPropertyBsasController", "retrieveUkPropertyBsas", "GET", "individuals/self-assessment/adjustable-summary/{nino}/uk-property/{calculationId}/{taxYear}", "Retrieve a UK property BSAS"),

    // Self Assessment Accounts
    EndpointDefinition("Self Assessment Accounts", "RetrieveChargeHistoryController", "retrieveChargeHistory", "GET", "accounts/self-assessment/{nino}/charges/{transactionId}", "Retrieve charge history"),
    EndpointDefinition("Self Assessment Accounts", "CreateOrAmendCodingOutController", "createOrAmendCodingOut", "PUT", "accounts/self-assessment/{nino}/{taxYear}/collection/tax-code", "Create or amend coding out amounts"),
    EndpointDefinition("Self Assessment Accounts", "RetrieveCodingOutStatusController", "retrieveCodingOutStatus", "GET", "accounts/self-assessment/{nino}/{taxYear}/collection/tax-code/coding-out/status", "Retrieve coding out status"),

    // ITSA Status
    EndpointDefinition("ITSA Status", "RetrieveItsaStatusController", "retrieveItsaStatus", "GET", "individuals/person/itsa-status/{nino}/{taxYear}", "Retrieve ITSA status"),

    // Foreign Income
    EndpointDefinition("Foreign Income", "RetrieveForeignIncomeController", "retrieveForeignIncome", "GET", "individuals/foreign-income/{nino}/{taxYear}", "Retrieve foreign income"),
    EndpointDefinition("Foreign Income", "AmendForeignIncomeController", "amendForeignIncome", "PUT", "individuals/foreign-income/{nino}/{taxYear}", "Create or amend foreign income"),

    // Other Income
    EndpointDefinition("Other Income", "RetrieveOtherIncomeController", "retrieveOtherIncome", "GET", "individuals/other-income/{nino}/{taxYear}", "Retrieve other income"),
    EndpointDefinition("Other Income", "AmendOtherIncomeController", "amendOtherIncome", "PUT", "individuals/other-income/{nino}/{taxYear}", "Create or amend other income"),

    // Other Deductions
    EndpointDefinition("Other Deductions", "RetrieveOtherDeductionsController", "retrieveOtherDeductions", "GET", "individuals/deductions/other/{nino}/{taxYear}", "Retrieve other deductions"),
    EndpointDefinition("Other Deductions", "AmendOtherDeductionsController", "amendOtherDeductions", "PUT", "individuals/deductions/other/{nino}/{taxYear}", "Create or amend other deductions"),

    // Insurance Policies Income
    EndpointDefinition("Insurance Policies Income", "RetrieveInsurancePoliciesController", "retrieveInsurancePolicies", "GET", "individuals/insurance-policies-income/{nino}/{taxYear}", "Retrieve insurance policies income"),
    EndpointDefinition("Insurance Policies Income", "AmendInsurancePoliciesController", "amendInsurancePolicies", "PUT", "individuals/insurance-policies-income/{nino}/{taxYear}", "Create or amend insurance policies income"),

    // Individual Disclosures
    EndpointDefinition("Individual Disclosures", "CreateMarriageAllowanceController", "createMarriageAllowance", "POST", "individuals/disclosures/marriage-allowance/{nino}", "Create a marriage allowance claim"),
    EndpointDefinition("Individual Disclosures", "RetrieveDisclosuresController", "retrieveDisclosures", "GET", "individuals/disclosures/{nino}/{taxYear}", "Retrieve disclosures"),
    EndpointDefinition("Individual Disclosures", "AmendDisclosuresController", "amendDisclosures", "PUT", "individuals/disclosures/{nino}/{taxYear}", "Create or amend disclosures"),

    // Individual Expenses
    EndpointDefinition("Individual Expenses", "RetrieveEmploymentExpensesController", "retrieveEmploymentExpenses", "GET", "individuals/expenses/employments/{nino}/{taxYear}", "Retrieve employment expenses"),
    EndpointDefinition("Individual Expenses", "AmendEmploymentExpensesController", "amendEmploymentExpenses", "PUT", "individuals/expenses/employments/{nino}/{taxYear}", "Create or amend employment expenses"),
    EndpointDefinition("Individual Expenses", "RetrieveOtherExpensesController", "retrieveOtherExpenses", "GET", "individuals/expenses/other/{nino}/{taxYear}", "Retrieve other expenses"),
    EndpointDefinition("Individual Expenses", "AmendOtherExpensesController", "amendOtherExpenses", "PUT", "individuals/expenses/other/{nino}/{taxYear}", "Create or amend other expenses"),

    // Partner Income
    EndpointDefinition("Partner Income", "ListPartnershipsController", "listPartnerships", "GET", "individuals/partner-income/{nino}/{taxYear}/partnership", "List partnership income"),
    EndpointDefinition("Partner Income", "AmendPartnershipIncomeController", "amendPartnershipIncome", "PUT", "individuals/partner-income/{nino}/{taxYear}/partnership", "Create or amend partnership income"),

    // Tax Liability Adjustments
    EndpointDefinition("Tax Liability Adjustments", "RetrieveTaxLiabilityAdjustmentsController", "retrieveTaxLiabilityAdjustments", "GET", "individuals/tax-liability/adjustments/{nino}/{taxYear}", "Retrieve tax liability adjustments"),
    EndpointDefinition("Tax Liability Adjustments", "AmendTaxLiabilityAdjustmentsController", "amendTaxLiabilityAdjustments", "PUT", "individuals/tax-liability/adjustments/{nino}/{taxYear}", "Create or amend tax liability adjustments"),

    // Self Assessment Assist
    EndpointDefinition("Self Assessment Assist", "ProduceAssistReportController", "produceAssistReport", "GET", "individuals/self-assessment/assist/reports/{nino}/{taxYear}/{calculationId}", "Produce a self assessment assist report"),

    // Business Income Summary
    EndpointDefinition("Business Income Summary", "RetrieveBusinessIncomeSummaryController", "retrieveBusinessIncomeSummary", "GET", "individuals/self-assessment/income-summary/{nino}/{typeOfBusiness}/{taxYear}/{businessId}", "Retrieve business income source summary"),

    // VAT (vat-api itself) — kept for regime tagging of mixed log files
    EndpointDefinition("VAT", "ObligationsController", "retrieveObligations", "GET", "organisations/vat/{vrn}/obligations", "Retrieve VAT obligations", Vat),
    EndpointDefinition("VAT", "SubmitReturnController", "submitReturn", "POST", "organisations/vat/{vrn}/returns", "Submit VAT return", Vat),
    EndpointDefinition("VAT", "ViewReturnController", "viewReturn", "GET", "organisations/vat/{vrn}/returns/{periodKey}", "View VAT return", Vat),
    EndpointDefinition("VAT", "LiabilitiesController", "retrieveLiabilities", "GET", "organisations/vat/{vrn}/liabilities", "Retrieve VAT liabilities", Vat)
  )

  private val byControllerEndpoint: Map[(String, String), EndpointDefinition] =
    all.map(d => (d.controller.toLowerCase, d.endpointName.toLowerCase) -> d).toMap

  private val pathMatchers: List[(Pattern, EndpointDefinition)] =
    all.map(d => buildPathRegex(d.pathTemplate) -> d)

  val apiAreas: List[String] = all.map(_.apiArea).distinct

  def tryMatch(controller: Option[String], endpointName: Option[String]): Option[EndpointDefinition] =
    for {
      c   <- controller
      e   <- endpointName
      d   <- byControllerEndpoint.get((c.toLowerCase, e.toLowerCase))
    } yield d

  def tryMatchPath(path: String, httpMethod: Option[String]): Option[EndpointDefinition] = {
    val trimmed = path.trim
    if (trimmed.isEmpty) None
    else {
      val withoutSlash = trimmed.dropWhile(_ == '/')
      val candidate    = withoutSlash.indexOf('?') match {
        case -1 => withoutSlash
        case i  => withoutSlash.substring(0, i)
      }
      pathMatchers.collectFirst {
        case (pattern, d)
            if pattern.matcher(candidate).matches() &&
              httpMethod.forall(_.equalsIgnoreCase(d.httpMethod)) =>
          d
      }
    }
  }

  def findByKey(key: String): Option[EndpointDefinition] =
    all.find(_.key.equalsIgnoreCase(key))

  private def buildPathRegex(template: String): Pattern = {
    val escaped = template.dropWhile(_ == '/').split("\\{[^/}]+\\}", -1).map(Pattern.quote).mkString("[^/]+")
    Pattern.compile("^" + escaped + "$", Pattern.CASE_INSENSITIVE)
  }
}
