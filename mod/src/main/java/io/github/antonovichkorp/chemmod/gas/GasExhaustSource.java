package io.github.antonovichkorp.chemmod.gas;

/**
 * World-side contract for an active ventilation block. Implementations return a first-order gas
 * removal rate in s^-1; the gas solver remains independent of optional kinetic-mod APIs.
 */
public interface GasExhaustSource {
    double gasRemovalRatePerSecond();
}
