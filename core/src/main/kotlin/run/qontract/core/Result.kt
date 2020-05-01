package run.qontract.core

import run.qontract.core.pattern.Pattern
import run.qontract.core.value.Value

sealed class Result {

    var scenario: Scenario? = null

    fun updateScenario(scenario: Scenario): Result {
        this.scenario = scenario
        return this
    }

    abstract fun isTrue(): Boolean

    class Success : Result() {

        override fun isTrue() = true
    }

    data class Failure(val message: String="", var cause: Failure? = null, val breadCrumb: String = "") : Result() {
        fun reason(errorMessage: String) = Failure(errorMessage, this)
        fun breadCrumb(breadCrumb: String) = Failure(cause = this, breadCrumb = breadCrumb)

        fun report(): FailureReport =
            (cause?.report() ?: FailureReport()).let { reason ->
                when {
                    message.isNotEmpty() -> reason.copy(errorMessages = listOf(message).plus(reason.errorMessages))
                    else -> reason
                }
            }.let { reason ->
                when {
                    breadCrumb.isNotEmpty() -> reason.copy(breadCrumbs = listOf(breadCrumb).plus(reason.breadCrumbs))
                    else -> reason
                }
            }

        override fun isTrue() = false
    }
}

data class FailureReport(val breadCrumbs: List<String> = emptyList(), val errorMessages: List<String> = emptyList())

fun mismatchResult(expected: String, actual: String): Result.Failure = Result.Failure("Expected $expected, actual was $actual")
fun mismatchResult(expected: String, actual: Value?): Result.Failure = mismatchResult(expected, valueError(actual) ?: "null")
fun mismatchResult(expected: Value, actual: Value?): Result = mismatchResult(valueError(expected) ?: "null", valueError(actual) ?: "")
fun mismatchResult(expected: Pattern, actual: String): Result.Failure = mismatchResult(expected.displayName, actual)
fun mismatchResult(pattern: Pattern, sampleData: Value?): Result.Failure = mismatchResult(pattern, sampleData?.toStringValue() ?: "null")


fun valueError(value: Value?): String? {
    return value?.let { "${it.displayableType()}: ${it.displayableValue()}" }
}

fun resultReport(result: Result): String {
    return when (result) {
        is Result.Failure -> {
            val firstLine = when(val scenario = result.scenario) {
                null -> ""
                else -> {
                    """In scenario "${scenario.name}""""
                }
            }

            val report = result.report().let { (breadCrumbs, errorMessages) ->
                val breadCrumbString =
                        breadCrumbs
                                .filter { it.isNotBlank() }
                                .joinToString(".") { it.trim() }
                                .let {
                                    when {
                                        it.isNotBlank() -> ">> $it"
                                        else -> ""
                                    }
                                }
                val errorMessagesString = errorMessages.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
                "$breadCrumbString\n\n$errorMessagesString".trim()
            }

            "$firstLine\n$report"
        }
        else -> ""
    }.trim()
}
