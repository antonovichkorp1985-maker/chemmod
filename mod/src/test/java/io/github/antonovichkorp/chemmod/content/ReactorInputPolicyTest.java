package io.github.antonovichkorp.chemmod.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactorInputPolicyTest {
    @Test
    void acceptsOnlyAnExactlyPurePhysicalComponent() {
        assertTrue(ReactorInputPolicy.isExactlyPure(new SubstanceContents("CCO", 1_000_000L, 1_000_000)));
        assertFalse(ReactorInputPolicy.isExactlyPure(new SubstanceContents("CCO", 1_000_000L, 999_999)));
        assertFalse(ReactorInputPolicy.isExactlyPure(new SubstanceContents("O", 1_000_000L, 0)));
        assertFalse(ReactorInputPolicy.isExactlyPure(null));
    }
}
