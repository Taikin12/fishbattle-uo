package dev.taikin.mcplugins.fishbattle;

import java.util.concurrent.ThreadLocalRandom;

final class DamageCalc {

    private DamageCalc() {
    }

    static int damage(Fish attacker, Fish defender, double attackerCharge, double defenderCharge,
            double typeMultiplier) {
        double attackFactor = 0.5 + attackerCharge * 2.0;
        double defenseFactor = 1.0 / (1.0 + defenderCharge * 0.6);
        double base = attacker.power() / 3.0 + attacker.level();
        double variance = 0.85 + ThreadLocalRandom.current().nextDouble() * 0.15;
        return Math.max(1,
                (int) Math.round(base * attackFactor * defenseFactor * typeMultiplier * variance));
    }

    static Technique bestTechnique(Fish fish) {
        Technique best = null;
        for (Technique technique : fish.moves()) {
            if (best == null || technique.bonus() > best.bonus()) {
                best = technique;
            }
        }
        return best;
    }
}
