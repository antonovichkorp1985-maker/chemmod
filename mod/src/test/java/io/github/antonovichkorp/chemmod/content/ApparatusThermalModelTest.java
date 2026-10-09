package io.github.antonovichkorp.chemmod.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApparatusThermalModelTest {
    private static final double AMBIENT = ApparatusThermalModel.AMBIENT_TEMPERATURE_KELVIN;

    @Test
    void timeConstantsFollowTheDeclaredCapacityAndConductances() {
        assertEquals(0.5, ApparatusThermalModel.warmingTimeConstantSeconds(), 1e-12);
        assertEquals(20.0, ApparatusThermalModel.coolingTimeConstantSeconds(), 1e-12);
    }

    @Test
    void coldVesselWarmsTowardButNeverInstantlyAdoptsTheSourceTemperature() {
        double afterOneSecond = ApparatusThermalModel.advance(AMBIENT, 1.0, 900.0);
        double plateau = ApparatusThermalModel.equilibriumTemperatureKelvin(900.0);
        assertEquals(808.98, afterOneSecond, 0.01);
        assertTrue(afterOneSecond > AMBIENT, "vessel did not warm at all");
        assertTrue(afterOneSecond < 900.0, "vessel adopted the source temperature instantly");
        assertTrue(afterOneSecond < plateau, "vessel overshot its own equilibrium");
        assertTrue(plateau < 900.0, "ambient loss must keep the plateau below the source");
    }

    @Test
    void heatingConvergesOnThePlateauWithoutOvershoot() {
        double plateau = ApparatusThermalModel.equilibriumTemperatureKelvin(650.0);
        double temperature = AMBIENT;
        double previous = temperature;
        // Ten one-second steps: still far enough from the plateau that every
        // increment is representable, so strict monotonicity is a real claim.
        for (int second = 0; second < 10; second++) {
            temperature = ApparatusThermalModel.advance(temperature, 1.0, 650.0);
            assertTrue(temperature > previous, "heating was not monotone at second " + second);
            assertTrue(temperature <= plateau + 1e-9, "heating overshot the plateau");
            previous = temperature;
        }
        // Closer than a double ulp from the plateau, further steps can only hold
        // it there; demanding another strict increase would test rounding noise.
        for (int second = 10; second < 40; second++) {
            temperature = ApparatusThermalModel.advance(temperature, 1.0, 650.0);
            assertTrue(temperature >= previous, "heating reversed at second " + second);
            assertTrue(temperature <= plateau + 1e-9, "heating overshot the plateau");
            previous = temperature;
        }
        assertEquals(plateau, temperature, 1e-9);
    }

    @Test
    void removingTheSourceCoolsTowardTheRoomWithoutUndershooting() {
        double hot = ApparatusThermalModel.equilibriumTemperatureKelvin(900.0);
        double previous = hot;
        // 400 one-second steps: twenty cooling time constants, so the body has
        // all but reached the room without ever crossing it.
        for (int second = 0; second < 400; second++) {
            double next = ApparatusThermalModel.advance(previous, 1.0, null);
            assertTrue(next < previous, "cooling was not monotone at second " + second);
            assertTrue(next > AMBIENT, "vessel cooled below room temperature");
            previous = next;
        }
        assertEquals(AMBIENT, previous, 1e-5);
        // A removed source still leaves the vessel usable for a while: this is
        // the observable difference from the old instantaneous temperature.
        assertTrue(ApparatusThermalModel.advance(hot, 10.0, null) > AMBIENT + 300.0);
    }

    @Test
    void aStrongerSourceReachesAHigherPlateau() {
        double furnace = ApparatusThermalModel.equilibriumTemperatureKelvin(650.0);
        double blastFurnace = ApparatusThermalModel.equilibriumTemperatureKelvin(900.0);
        assertTrue(furnace < blastFurnace, "source ladder was not preserved");
        assertTrue(furnace > AMBIENT && blastFurnace < 900.0);
    }

    @Test
    void zeroElapsedTimeLeavesTheBodyExactlyAlone() {
        assertEquals(412.5, ApparatusThermalModel.advance(412.5, 0.0, 900.0), 0.0);
        assertEquals(412.5, ApparatusThermalModel.advance(412.5, 0.0, null), 0.0);
    }

    @Test
    void subdividedStepsMatchOneLongStep() {
        double single = ApparatusThermalModel.advance(AMBIENT, 1.0, 900.0);
        double subdivided = AMBIENT;
        for (int step = 0; step < 20; step++) {
            subdivided = ApparatusThermalModel.advance(subdivided, 0.05, 900.0);
        }
        assertEquals(single, subdivided, 1e-9);
    }

    @Test
    void invalidInputsAreRejectedInsteadOfClamped() {
        double[] invalid = { -1.0, Double.NaN, Double.POSITIVE_INFINITY };
        for (double value : invalid) {
            assertThrows(IllegalArgumentException.class, () -> ApparatusThermalModel.advance(value, 1.0, 900.0));
            assertThrows(IllegalArgumentException.class, () -> ApparatusThermalModel.advance(300.0, value, 900.0));
            assertThrows(IllegalArgumentException.class, () -> ApparatusThermalModel.advance(300.0, 1.0, value));
            assertThrows(IllegalArgumentException.class, () -> ApparatusThermalModel.equilibriumTemperatureKelvin(value));
        }
        // Absolute zero is a legal state, not an error: it is left alone when no
        // time passes, and the room warms it instead of driving it negative.
        assertEquals(0.0, ApparatusThermalModel.advance(0.0, 0.0, null), 0.0);
        assertTrue(ApparatusThermalModel.advance(0.0, 1.0, null) > 0.0);
    }
}
