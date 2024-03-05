package parser

import kotlin.test.assertEquals

fun check(expectedToken: AntlrToken, actualToken: AntlrToken, getTokenValue: (AntlrToken) -> String?) {
    assertEquals(expectedToken.type, actualToken.type, "Expected token type: ${expectedToken.type.name}")
    assertEquals(expectedToken.channel, actualToken.channel, "Expected token channel: ${expectedToken.channel.name}")
    if (expectedToken.value != null) {
        assertEquals(expectedToken.value, getTokenValue(actualToken), "Expected token value: ${expectedToken.value}")
    }
}