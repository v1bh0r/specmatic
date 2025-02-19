package `in`.specmatic.core

import `in`.specmatic.GENERATION
import `in`.specmatic.core.pattern.*
import `in`.specmatic.core.value.JSONObjectValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.StringValue
import `in`.specmatic.core.value.Value
import io.ktor.util.reflect.*
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Tag
import java.util.function.Consumer
import kotlin.collections.HashMap

internal class HttpHeadersPatternTest {
    @Test
    fun `should match a header`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("value", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "value"

        val result = httpHeaders.matches(headers, Resolver())
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `header name match should be case insensitive`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("value", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["KEY"] = "value"

        val result = httpHeaders.matches(headers, Resolver())
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `ancestor header name match should be case insensitive`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("value", "key")), mapOf("key" to stringToPattern("value", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["KEY"] = "value"

        val result = httpHeaders.matches(headers, Resolver())
        println(result.reportString())

        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `unexpected header match should be case insensitive`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("value", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "value"
        headers["unexpected"] = "value"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should not accept duplicate headers with different case`() {
        assertThatThrownBy {
            HttpHeadersPattern(mapOf("key" to stringToPattern("value", "key"), "KEY" to stringToPattern("value", "KEY")))
        }.satisfies(Consumer {
            assertThat(it).instanceOf(ContractException::class)
        })
    }

    @Test
    fun `should pattern match a numeric string`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key"), "expected" to stringToPattern("(number)", "expected")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "123"
        headers["Expected"] = "123"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should pattern match string`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(string)", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "abc123"
        assertThat(httpHeaders.matches(headers, Resolver()) is Result.Success).isTrue()
    }

    @Test
    fun `should not pattern match a numeric string when value has alphabets`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["key"] = "abc"
        httpHeaders.matches(headers, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).toMatchFailureDetails()).isEqualTo(MatchFailureDetails(listOf("HEADERS", "key"), listOf("Expected number, actual was \"abc\"")))
        }
    }

    @Test
    fun `should not match when header is not present`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key")))
        val headers: HashMap<String, String> = HashMap()
        headers["anotherKey"] = "123"
        httpHeaders.matches(headers, Resolver()).let {
            assertThat(it is Result.Failure).isTrue()
            assertThat((it as Result.Failure).toMatchFailureDetails())
                    .isEqualTo(MatchFailureDetails(listOf("HEADERS", "key"), listOf("Expected header named \"key\" was missing")))
        }
    }

    @Test
    fun `should not add numericString pattern to the resolver`() {
        val httpHeaders = HttpHeadersPattern(mapOf("key" to stringToPattern("(number)", "key")))
        val resolver = Resolver()
        httpHeaders.matches(HashMap(), resolver)
        assertThat(resolver.matchesPattern(null, resolver.getPattern("(number)"), StringValue("123")) is Result.Failure).isTrue()
    }

    @Test
    fun `should generate values`() {
        val httpHeaders = HttpHeadersPattern(
                mapOf("exactKey" to stringToPattern("value", "exactKey"), "numericKey" to stringToPattern("(number)", "numericKey"), "stringKey" to stringToPattern("(string)", "stringKey"), "serverStateKey" to stringToPattern("(string)", "serverStateKey")))
        val facts: HashMap<String, Value> = hashMapOf("serverStateKey" to StringValue("serverStateValue"))
        val resolver = Resolver(facts)
        val generatedResult = httpHeaders.generate(resolver)
        generatedResult.let {
            assertThat(it["exactKey"]).isEqualTo("value")
            assertThat(it["numericKey"]).matches("[0-9]+")
            assertThat(it["stringKey"]).matches("[0-9a-zA-Z]+")
            assertThat(it["serverStateKey"]).isEqualTo("serverStateValue")
        }
    }

    @Test
    fun `should generate json object values as unformatted strings`() {
        val httpHeaders = HttpHeadersPattern(
            mapOf("jsonHeaderKey" to ExactValuePattern(
                JSONObjectValue(jsonObject = mapOf("key" to StringValue("value"))))
            )
        )
        val generatedValue = httpHeaders.generate(Resolver())
        assertThat(generatedValue["jsonHeaderKey"]).isEqualTo("""{"key":"value"}""")
    }

    @Test
    fun `should not attempt to validate or match additional headers`() {
        val expectedHeaders = HttpHeadersPattern(mapOf("Content-Type" to stringToPattern("(string)", "Content-Type")))

        val actualHeaders = HashMap<String, String>().apply {
            put("Content-Type", "application/json")
            put("X-Unspecified-Header", "Should be ignored")
        }

        assertThat(expectedHeaders.matches(actualHeaders, Resolver()).isSuccess()).isTrue()
    }

    @Test
    fun `should validate extra headers when mocking`() {
        val expectedHeaders = HttpHeadersPattern(mapOf("X-Expected" to stringToPattern("(string)", "X-Expected")))

        val actualHeaders = HashMap<String, String>().apply {
            put("X-Expected", "application/json")
            put("X-Unspecified-Header", "Can't accept this header in a mock")
        }

        assertThat(expectedHeaders.matches(actualHeaders, Resolver(findKeyErrorCheck = DefaultKeyCheck.disableOverrideUnexpectedKeycheck()))).isInstanceOf(Result.Failure::class.java)
    }

    @Tag(GENERATION)
    @Test
    fun `should generate new header objects given an empty row`() {
        val headers = HttpHeadersPattern(mapOf("Content-Type" to stringToPattern("(string)", "Content-Type")))
        val newHeaders = headers.newBasedOn(Row(), Resolver())
        assertEquals("(string)", newHeaders[0].pattern.getValue("Content-Type").toString())
    }

    @Tag(GENERATION)
    @Test
    fun `should generate new header object with the value of the example in the given row`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to StringPattern()))
        val newHeaders = headers.newBasedOn(Row(mapOf("X-TraceID" to "123")), Resolver())
        assertThat(newHeaders[0].pattern.getValue("X-TraceID")).isEqualTo(ExactValuePattern(StringValue("123")))
    }

    @Tag(GENERATION)
    @Test
    fun `should generate two header object given one optional header and an empty row`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to StringPattern(), "X-Identifier?" to StringPattern()))
        val newHeaders = headers.newBasedOn(Row(), Resolver())

        assertThat(newHeaders).containsExactlyInAnyOrder(
            HttpHeadersPattern(mapOf("X-TraceID" to StringPattern())),
            HttpHeadersPattern(mapOf("X-TraceID" to StringPattern(), "X-Identifier" to StringPattern()))
        )
    }

    @Tag(GENERATION)
    @Test
    fun `should generate two header object given one optional header an example of the mandatory header`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to StringPattern(), "X-Identifier?" to StringPattern()))
        val newHeaders = headers.newBasedOn(Row(mapOf("X-TraceID" to "123")), Resolver())

        assertThat(newHeaders).containsExactlyInAnyOrder(
            HttpHeadersPattern(mapOf("X-TraceID" to ExactValuePattern(StringValue("123")))),
            HttpHeadersPattern(mapOf("X-TraceID" to ExactValuePattern(StringValue("123")), "X-Identifier" to StringPattern()))
        )
    }

    @Tag(GENERATION)
    @Test
    fun `should generate one header object given one optional header an example of the optional header`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to StringPattern()))
        val newHeaders = headers.newBasedOn(Row(mapOf("X-TraceID" to "123")), Resolver())
        assertThat(newHeaders[0].pattern.getValue("X-TraceID")).isEqualTo(ExactValuePattern(StringValue("123")))
    }

    @Tag(GENERATION)
    @Test
    fun `should generate negative values for a string`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to StringPattern()))
        val newHeaders = headers.negativeBasedOn(Row(), Resolver())

        assertThat(newHeaders).isEmpty()
    }

    @Tag(GENERATION)
    @Test
    fun `should generate negative values for a number`() {
        val headers = HttpHeadersPattern(mapOf("X-TraceID" to NumberPattern()))
        val newHeaders = headers.negativeBasedOn(Row(), Resolver())

        assertThat(newHeaders).containsExactlyInAnyOrder(
            HttpHeadersPattern(mapOf("X-TraceID" to StringPattern())),
            HttpHeadersPattern(mapOf("X-TraceID" to BooleanPattern())),
        )
    }

    @Test
    fun `given ancestor headers from the creators header set the pattern should skip past all headers in the value map not in the ancestors list`() {
        val pattern = HttpHeadersPattern(mapOf("X-Required-Header" to StringPattern()), mapOf("X-Required-Header" to StringPattern()))
        val headers = mapOf("X-Required-Header" to "some value", "X-Extraneous-Header" to "some other value")

        assertThat(pattern.matches(headers, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should match if optional headers are present`() {
        val pattern = HttpHeadersPattern(mapOf("X-Optional-Header?" to StringPattern()))
        val headers = mapOf("X-Optional-Header?" to "some value")

        assertThat(pattern.matches(headers, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `should match if optional headers are absent`() {
        val pattern = HttpHeadersPattern(mapOf("X-Optional-Header?" to StringPattern()))
        val headers = emptyMap<String, String>()

        assertThat(pattern.matches(headers, Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Tag(GENERATION)
    @Test
    fun `an optional header should result in 2 new header patterns for newBasedOn`() {
        val pattern = HttpHeadersPattern(mapOf("X-Optional?" to StringPattern()))
        val list = pattern.newBasedOn(Row(), Resolver())

        assertThat(list).hasSize(2)

        val flags = list.map {
            when {
                it.pattern.contains("X-Optional") -> "with"
                else -> "without"
            }
        }

        flagsContain(flags, listOf("with", "without"))
    }

    @Test
    fun `it should encompass itself`() {
        val headersType = HttpHeadersPattern(mapOf("X-Required" to StringPattern()))
        assertThat(headersType.encompasses(headersType, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `a header pattern with required headers should encompass one with extra headers`() {
        val bigger = HttpHeadersPattern(mapOf("X-Required" to StringPattern()))
        val smaller = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Extra" to StringPattern()))
        assertThat(bigger.encompasses(smaller, Resolver(), Resolver())).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `a header pattern with an optional header should match one without that header`() {
        val bigger = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Optional?" to NumberPattern()))
        val smaller = HttpHeadersPattern(mapOf("X-Required" to StringPattern()))
        val result = bigger.encompasses(smaller, Resolver(), Resolver())
        assertThat(result).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `a header pattern with an optional header should match one with that header if present`() {
        val bigger = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Optional?" to NumberPattern()))
        val smaller = HttpHeadersPattern(mapOf("X-Required" to StringPattern(), "X-Optional" to StringPattern()))
        val result = bigger.encompasses(smaller, Resolver(), Resolver())
        assertThat(result).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `should match a pattern only when resolver has mock matching on`() {
        val headersPattern = HttpHeadersPattern(mapOf("X-Data" to NumberPattern()))
        assertThat(headersPattern.matches(mapOf("X-Data" to "10"), Resolver())).isInstanceOf(Result.Success::class.java)
        assertThat(headersPattern.matches(mapOf("X-Data" to "(number)"), Resolver(mockMode = true))).isInstanceOf(Result.Success::class.java)
        assertThat(headersPattern.matches(mapOf("X-Data" to "(number)"), Resolver(mockMode = false))).isInstanceOf(Result.Failure::class.java)
    }

    @Test
    fun `unexpected standard http headers are allowed and will not break a match check`() {
        val headersPattern = HttpHeadersPattern(mapOf("X-Data" to StringPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Content-Type?" to StringPattern()))
        val resolver = Resolver()
        assertThat(headersPattern.matches(mapOf("X-Data" to "data", "Content-Type" to "text/plain"), resolver)).isInstanceOf(Result.Success::class.java)
    }

    @Test
    fun `unexpected but non-optional standard http headers are allowed and will break a match check`() {
        val headersPattern = HttpHeadersPattern(mapOf("X-Data" to StringPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Content-Type" to StringPattern()))
        val resolver = Resolver(findKeyErrorCheck = DefaultKeyCheck.disableOverrideUnexpectedKeycheck())
        assertThat(headersPattern.matches(mapOf("X-Data" to "data", "Content-Type" to "text/plain"), resolver)).isInstanceOf(Result.Failure::class.java)
    }

    @Nested
    inner class ReturnMultipleErrrors {
        val headersPattern = HttpHeadersPattern(mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()))
        val resolver = Resolver()
        val result = headersPattern.matches(mapOf("Y-Data" to "data"), resolver)

        @Test
        fun `should return as many errors as there are problems`() {
            result as Result.Failure

            assertThat(result.toMatchFailureDetailList()).hasSize(2)
        }

        @Test
        fun `errors should mention the name of header`() {
            result as Result.Failure

            assertThat(result.toFailureReport().toText()).contains(">> HEADERS.X-Data")
            assertThat(result.toFailureReport().toText()).contains(">> HEADERS.Y-Data")

            println(result.toFailureReport().toText())
        }

        @Test
        fun `key errors appear before value errors`() {
            result as Result.Failure

            val resultText = result.toFailureReport().toText()

            assertThat(resultText.indexOf(">> HEADERS.X-Data")).isLessThan(resultText.indexOf(">> HEADERS.Y-Data"))

            println(result.toFailureReport().toText())
        }
    }

    @Test
    fun `all missing header backward compatibility errors together`() {
        val older = HttpHeadersPattern()
        val newer = HttpHeadersPattern(mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()))

        val resultText = newer.encompasses(older, Resolver(), Resolver()).reportString()
        println(resultText)

        assertThat(resultText).contains("HEADER.X-Data")
        assertThat(resultText).contains("HEADER.Y-Data")
    }

    @Test
    fun `header presence and value backward compatibility errors together`() {
        val older = HttpHeadersPattern(mapOf("X-Data" to NumberPattern()))
        val newer = HttpHeadersPattern(mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()))

        val resultText = newer.encompasses(older, Resolver(), Resolver()).reportString()
        println(resultText)

        assertThat(resultText).contains("HEADER.X-Data")
        assertThat(resultText).contains("HEADER.Y-Data")
    }

    @Test
    fun `all header value backward compatibility errors together`() {
        val older = HttpHeadersPattern(mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()))
        val newer = HttpHeadersPattern(mapOf("X-Data" to NumberPattern(), "Y-Data" to StringPattern()), ancestorHeaders = mapOf("X-Data" to StringPattern(), "Y-Data" to NumberPattern()))

        val resultText = newer.encompasses(older, Resolver(), Resolver()).reportString()

        assertThat(resultText).contains("HEADER.X-Data")
        assertThat(resultText).contains("HEADER.Y-Data")
    }
}
