package `in`.specmatic.core.pattern

import `in`.specmatic.GENERATION
import `in`.specmatic.core.Resolver
import `in`.specmatic.core.UseDefaultExample
import `in`.specmatic.core.value.BooleanValue
import org.junit.jupiter.api.Test
import `in`.specmatic.core.value.NullValue
import `in`.specmatic.shouldNotMatch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag

internal class BooleanPatternTest {
    @Test
    fun `should fail to match nulls gracefully`() {
        NullValue shouldNotMatch BooleanPattern()
    }

    @Test
    fun `it should use the example if provided when generating`() {
        val generated = BooleanPattern(example = "true").generate(Resolver(defaultExampleResolver = UseDefaultExample))
        assertThat(generated).isEqualTo(BooleanValue(true))
    }

    @Test
    @Tag(GENERATION)
    fun `negative patterns should be generated`() {
        val result = BooleanPattern().negativeBasedOn(Row(), Resolver())
        assertThat(result.map { it.typeName }).containsExactlyInAnyOrder(
            "null"
        )
    }
}