package io.github.antonovichkorp.chemmod.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorInputPolicyTest {
    @Test
    void acceptsOnlyTheExactPurePpmValue() {
        assertTrue(ReactorInputPolicy.isExactlyPurePpm(1_000_000));
        assertFalse(ReactorInputPolicy.isExactlyPurePpm(999_999));
        assertFalse(ReactorInputPolicy.isExactlyPurePpm(0));
        assertFalse(ReactorInputPolicy.isExactlyPurePpm(1_000_001));
    }
}
