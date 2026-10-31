package io.github.antonovichkorp.chemmod.core.electrical

/**
 * Circuit breaker state machine and parameters for ElectricalNetwork integration.
 * Object CircuitBreakerBranch provides state types (Closed/Tripped/OpenTripped),
 * Settings data class, and factory function create(ratedA, iTripInstantA, thermalConstantA2S).
 * The data class CircuitBreakerBranch(...) : Branch is defined in ElectricalNetwork.kt
 * and integrates with the G*V=I solver via addCircuitBreaker() and the solve() fixed-point loop.
 */